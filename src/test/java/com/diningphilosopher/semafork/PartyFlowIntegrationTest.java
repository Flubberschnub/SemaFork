package com.diningphilosopher.semafork;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
public class PartyFlowIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("semafork_test")
            .withUsername("testuser")
            .withPassword("testpassword");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JsonMapper jsonMapper;

    @Test
    void fullPartyVotingFlow_shouldFinalizeWinner() throws Exception {
        String createPartyResponse = mockMvc.perform(post("/api/parties")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "name": "Lunch Crew"
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode partyJson = jsonMapper.readTree(createPartyResponse);
        long partyId = partyJson.get("id").asLong();

        String joinKevinResponse = mockMvc.perform(post("/api/parties/{partyId}/members", partyId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                "memberName": "Kevin"
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode kevinJson = jsonMapper.readTree(joinKevinResponse);
        long kevinId = kevinJson.get("id").asLong();

        String joinAlexResponse = mockMvc.perform(post("/api/parties/{partyId}/members", partyId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                "memberName": "Alex"
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode alexJson = jsonMapper.readTree(joinAlexResponse);
        long alexId = alexJson.get("id").asLong();

        String cavaResponse = mockMvc.perform(post("/api/parties/{partyId}/suggestions", partyId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("""
                                {
                                "memberId": %d,
                                "name": "Cava"
                                }
                                """, kevinId)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode cavaJson = jsonMapper.readTree(cavaResponse);
        long cavaId = cavaJson.get("id").asLong();

        String hatsuyukiResponse = mockMvc.perform(post("/api/parties/{partyId}/suggestions", partyId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("""
                                {
                                "memberId": %d,
                                "name": "Hatsuyuki"
                                }
                                """, alexId)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode hatsuyukiJson = jsonMapper.readTree(hatsuyukiResponse);
        long hatsuyukiId = hatsuyukiJson.get("id").asLong();

        mockMvc.perform(post("/api/parties/{partyId}/voting/start-voting", partyId))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/parties/{partyId}/voting/votes", partyId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("""
                                {
                                "memberId": %d,
                                "suggestionId": %d
                                }
                                """, kevinId, cavaId)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/parties/{partyId}/voting/votes", partyId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("""
                                {
                                "memberId": %d,
                                "suggestionId": %d
                                }
                                """, alexId, cavaId)))
                .andExpect(status().isCreated());

        String resultsResponse = mockMvc.perform(get("/api/parties/{partyId}/voting", partyId))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode resultsJson = jsonMapper.readTree(resultsResponse);

        assertEquals(partyId, resultsJson.get("partyId").asLong());
        assertEquals("FINALIZED", resultsJson.get("status").asString(""));
        assertNotNull(resultsJson.get("winnerSuggestionId"));
        assertEquals(cavaId, resultsJson.get("winnerSuggestionId").asLong());
        assertEquals("Cava", resultsJson.get("winnerSuggestionName").asString(""));

        JsonNode countsNode = resultsJson.get("counts");
        assertEquals(2L, countsNode.get(String.valueOf(cavaId)).asLong());
    }

}
