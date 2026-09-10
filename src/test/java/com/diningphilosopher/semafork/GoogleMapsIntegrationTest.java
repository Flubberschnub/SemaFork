package com.diningphilosopher.semafork;

import com.diningphilosopher.semafork.controller.MapsConfigController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest(properties = {"GOOGLE_MAPS_BROWSER_KEY=test-browser-key", "GOOGLE_MAPS_MAP_ID=test-map-id"})
@AutoConfigureMockMvc
class GoogleMapsIntegrationTest {
    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired private MockMvc mvc;
    @Autowired private JsonMapper json;

    @Test
    void placeIdsRoundTripAndVoteWithoutPersistingGoogleContent() throws Exception {
        JsonNode host = createParty();
        long partyId = host.get("partyId").asLong();
        String token = host.get("memberToken").asString();
        JsonNode first = read(suggest(partyId, token, Map.of("googlePlaceId", "ChIJ_first_branch"))
                .andExpect(status().isCreated()));
        long firstId = first.get("id").asLong();
        assertEquals("ChIJ_first_branch", first.get("googlePlaceId").asString());
        assertTrue(first.get("name").isNull());
        assertFalse(first.has("rating"));
        assertFalse(first.has("photos"));
        assertFalse(first.has("reviews"));
        assertFalse(first.has("location"));
        assertFalse(first.has("memberToken"));

        suggest(partyId, token, Map.of("googlePlaceId", "ChIJ_second_branch")).andExpect(status().isCreated());
        JsonNode listed = read(mvc.perform(get("/api/parties/{id}/suggestions", partyId)).andExpect(status().isOk()));
        assertEquals(2, listed.size());
        assertEquals("ChIJ_first_branch", listed.get(0).get("googlePlaceId").asString());

        mvc.perform(post("/api/parties/{id}/voting/start", partyId)
                .header("X-Host-Token", host.get("hostToken").asString())).andExpect(status().isNoContent());
        mvc.perform(post("/api/parties/{id}/voting/votes", partyId)
                .header("X-Member-Token", token).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("suggestionId", firstId)))).andExpect(status().isCreated());
        JsonNode result = read(mvc.perform(get("/api/parties/{id}/voting", partyId)).andExpect(status().isOk()));
        assertEquals("FINALIZED", result.get("status").asString());
        assertEquals(firstId, result.get("winnerSuggestionId").asLong());
        assertTrue(result.get("winnerSuggestionName").isNull()); // Browser resolves the winning Place ID.
        assertEquals(1, result.get("counts").get(Long.toString(firstId)).asInt());
    }

    @Test
    void duplicatePlaceIsRejectedOnlyWithinItsPartyAndManualNamesStillWork() throws Exception {
        JsonNode host = createParty();
        long id = host.get("partyId").asLong();
        String token = host.get("memberToken").asString();
        suggest(id, token, Map.of("googlePlaceId", "ChIJ_same_branch")).andExpect(status().isCreated());
        suggest(id, token, Map.of("googlePlaceId", "ChIJ_same_branch")).andExpect(status().isConflict());
        suggest(id, token, Map.of("googlePlaceId", "ChIJ_other_branch")).andExpect(status().isCreated());
        JsonNode manual = read(suggest(id, token, Map.of("name", "  Local cafe  ")).andExpect(status().isCreated()));
        assertEquals("Local cafe", manual.get("name").asString());
        assertTrue(manual.get("googlePlaceId").isNull());
        suggest(id, token, Map.of("name", "local CAFE")).andExpect(status().isConflict());

        JsonNode other = createParty();
        suggest(other.get("partyId").asLong(), other.get("memberToken").asString(),
                Map.of("googlePlaceId", "ChIJ_same_branch")).andExpect(status().isCreated());
    }

    @Test
    void candidateSourceValidationRejectsAmbiguousAndInvalidPayloads() throws Exception {
        JsonNode host = createParty();
        long id = host.get("partyId").asLong();
        String token = host.get("memberToken").asString();
        String[] invalid = {
                "{}", "{\"name\":\"   \"}", "{\"googlePlaceId\":\"\"}",
                "{\"googlePlaceId\":\"   \"}",
                "{\"name\":\"Forged place name\",\"googlePlaceId\":\"ChIJ_somewhere\"}",
                "{\"googlePlaceId\":\"https://example.com/restaurant\"}",
                "{\"googlePlaceId\":\"<script>bad</script>\"}",
                json.writeValueAsString(Map.of("googlePlaceId", "a".repeat(256))),
                json.writeValueAsString(Map.of("name", "a".repeat(121)))
        };
        for (String body : invalid) {
            mvc.perform(post("/api/parties/{id}/suggestions", id).header("X-Member-Token", token)
                    .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());
        }
        assertEquals(0, read(mvc.perform(get("/api/parties/{id}/suggestions", id))).size());
    }

    @Test
    void mapsCandidatesKeepParticipantAndPhaseGuards() throws Exception {
        JsonNode host = createParty();
        JsonNode other = createParty();
        long id = host.get("partyId").asLong();
        String token = host.get("memberToken").asString();
        Map<String, String> pick = Map.of("googlePlaceId", "ChIJ_protected");
        suggest(id, "invalid", pick).andExpect(status().isUnauthorized());
        suggest(id, other.get("memberToken").asString(), pick).andExpect(status().isUnauthorized());
        suggest(id, token, pick).andExpect(status().isCreated());
        suggest(id, token, Map.of("name", "Manual alternative")).andExpect(status().isCreated());
        mvc.perform(post("/api/parties/{id}/voting/start", id)
                .header("X-Host-Token", host.get("hostToken").asString())).andExpect(status().isNoContent());
        suggest(id, token, Map.of("googlePlaceId", "ChIJ_late")).andExpect(status().isBadRequest());
    }

    @Test
    void runtimeConfigurationExposesOnlyBrowserSettingsAndDisablesCleanly() throws Exception {
        var response = mvc.perform(get("/api/config/maps")).andExpect(status().isOk())
                .andReturn().getResponse();
        assertEquals("no-store", response.getHeader("Cache-Control"));
        JsonNode config = json.readTree(response.getContentAsString());
        assertEquals(3, config.size());
        assertTrue(config.get("enabled").asBoolean());
        assertEquals("test-browser-key", config.get("browserKey").asString());
        assertEquals("test-map-id", config.get("mapId").asString());

        var disabled = Objects.requireNonNull(new MapsConfigController("  ", "").mapsConfig().getBody());
        assertFalse(disabled.enabled());
        assertEquals("", disabled.browserKey());
        assertEquals("DEMO_MAP_ID", disabled.mapId());
    }

    private JsonNode createParty() throws Exception {
        return read(mvc.perform(post("/api/parties").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Maps test party\",\"hostName\":\"Host\"}"))
                .andExpect(status().isCreated()));
    }

    private ResultActions suggest(long partyId, String token, Map<String, String> body) throws Exception {
        return mvc.perform(post("/api/parties/{id}/suggestions", partyId).header("X-Member-Token", token)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)));
    }

    private JsonNode read(ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsString());
    }
}
