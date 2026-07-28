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
class PartyFlowIntegrationTest {

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
    void completePartyFlowAutomaticallyFinalizesWhenEveryoneVotes() throws Exception {
        PartySession host = createParty("Lunch Crew", "Kevin");
        MemberSession alex = joinParty(host.joinCode(), "Alex");

        long cavaId = addSuggestion(host.partyId(), host.memberToken(), "Cava");
        addSuggestion(host.partyId(), alex.memberToken(), "Hatsuyuki");

        startVoting(host.partyId(), host.hostToken());
        castVote(host.partyId(), host.memberToken(), cavaId)
                .andExpect(status().isCreated());
        castVote(host.partyId(), alex.memberToken(), cavaId)
                .andExpect(status().isCreated());

        JsonNode results = getResults(host.partyId());
        assertEquals("FINALIZED", results.get("status").asString());
        assertEquals(cavaId, results.get("winnerSuggestionId").asLong());
        assertEquals("Cava", results.get("winnerSuggestionName").asString());
        assertEquals(2L, results.get("counts").get(String.valueOf(cavaId)).asLong());
    }

    @Test
    void hostCanFinalizeWithCurrentVotesWhenSomeoneIsUnavailable() throws Exception {
        PartySession host = createParty("Dinner", "Host");
        joinParty(host.joinCode(), "Unavailable friend");

        long firstId = addSuggestion(host.partyId(), host.memberToken(), "First place");
        addSuggestion(host.partyId(), host.memberToken(), "Second place");
        startVoting(host.partyId(), host.hostToken());
        castVote(host.partyId(), host.memberToken(), firstId)
                .andExpect(status().isCreated());

        JsonNode inProgress = getResults(host.partyId());
        assertEquals("VOTING", inProgress.get("status").asString());
        assertEquals(0, inProgress.get("counts").size());

        mockMvc.perform(post("/api/parties/{partyId}/voting/finalize", host.partyId())
                        .header("X-Host-Token", host.hostToken()))
                .andExpect(status().isNoContent());

        JsonNode finalized = getResults(host.partyId());
        assertEquals("FINALIZED", finalized.get("status").asString());
        assertEquals(firstId, finalized.get("winnerSuggestionId").asLong());
    }

    @Test
    void authorizationAndDatabaseRulesProtectTheVotingFlow() throws Exception {
        PartySession host = createParty("Protected party", "Host");
        MemberSession guest = joinParty(host.joinCode(), "Guest");

        addSuggestion(host.partyId(), host.memberToken(), "Only one");

        mockMvc.perform(post("/api/parties/{partyId}/voting/start", host.partyId())
                        .header("X-Host-Token", "not-the-host"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/parties/{partyId}/voting/start", host.partyId())
                        .header("X-Host-Token", host.hostToken()))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/parties/{partyId}/suggestions", host.partyId())
                        .header("X-Member-Token", host.memberToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":" only one "}
                                """))
                .andExpect(status().isConflict());

        long secondId = addSuggestion(host.partyId(), guest.memberToken(), "Second option");
        startVoting(host.partyId(), host.hostToken());

        castVote(host.partyId(), host.memberToken(), secondId)
                .andExpect(status().isCreated());
        castVote(host.partyId(), host.memberToken(), secondId)
                .andExpect(status().isConflict());
    }

    private PartySession createParty(String partyName, String hostName) throws Exception {
        String response = mockMvc.perform(post("/api/parties")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(new CreateRequest(partyName, hostName))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        JsonNode json = jsonMapper.readTree(response);
        assertNotNull(json.get("hostToken"));
        assertNotNull(json.get("memberToken"));
        return new PartySession(
                json.get("partyId").asLong(),
                json.get("joinCode").asString(),
                json.get("hostToken").asString(),
                json.get("memberToken").asString()
        );
    }

    private MemberSession joinParty(String joinCode, String memberName) throws Exception {
        String response = mockMvc.perform(post("/api/parties/join")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(new JoinRequest(joinCode, memberName))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        JsonNode json = jsonMapper.readTree(response);
        return new MemberSession(json.get("memberId").asLong(), json.get("memberToken").asString());
    }

    private long addSuggestion(long partyId, String memberToken, String name) throws Exception {
        String response = mockMvc.perform(post("/api/parties/{partyId}/suggestions", partyId)
                        .header("X-Member-Token", memberToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(new SuggestionRequest(name))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return jsonMapper.readTree(response).get("id").asLong();
    }

    private void startVoting(long partyId, String hostToken) throws Exception {
        mockMvc.perform(post("/api/parties/{partyId}/voting/start", partyId)
                        .header("X-Host-Token", hostToken))
                .andExpect(status().isNoContent());
    }

    private org.springframework.test.web.servlet.ResultActions castVote(
            long partyId,
            String memberToken,
            long suggestionId
    ) throws Exception {
        return mockMvc.perform(post("/api/parties/{partyId}/voting/votes", partyId)
                .header("X-Member-Token", memberToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(new VoteRequest(suggestionId))));
    }

    private JsonNode getResults(long partyId) throws Exception {
        String response = mockMvc.perform(get("/api/parties/{partyId}/voting", partyId))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return jsonMapper.readTree(response);
    }

    private record PartySession(long partyId, String joinCode, String hostToken, String memberToken) {}
    private record MemberSession(long memberId, String memberToken) {}
    private record CreateRequest(String name, String hostName) {}
    private record JoinRequest(String joinCode, String memberName) {}
    private record SuggestionRequest(String name) {}
    private record VoteRequest(long suggestionId) {}
}
