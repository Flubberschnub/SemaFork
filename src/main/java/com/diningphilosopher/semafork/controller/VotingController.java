package com.diningphilosopher.semafork.controller;

import com.diningphilosopher.semafork.dto.vote.CastVoteRequest;
import com.diningphilosopher.semafork.dto.vote.VoteResultResponse;
import com.diningphilosopher.semafork.service.VotingService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/parties/{partyId}/voting")
public class VotingController {

    private final VotingService votingService;

    public VotingController(VotingService votingService) {
        this.votingService = votingService;
    }

    @PostMapping("/start")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void startVoting(
            @PathVariable long partyId,
            @RequestHeader("X-Host-Token") String hostToken
    ) {
        votingService.startVoting(partyId, hostToken);
    }

    @PostMapping("/votes")
    @ResponseStatus(HttpStatus.CREATED)
    public void castVote(
            @PathVariable long partyId,
            @RequestHeader("X-Member-Token") String memberToken,
            @Valid @RequestBody CastVoteRequest request
    ) {
        votingService.castVote(partyId, memberToken, request);
    }

    @PostMapping("/finalize")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void finalizeVoting(
            @PathVariable long partyId,
            @RequestHeader("X-Host-Token") String hostToken
    ) {
        votingService.finalizeVoting(partyId, hostToken);
    }

    @GetMapping
    public VoteResultResponse results(@PathVariable long partyId) {
        return votingService.getResults(partyId);
    }
}
