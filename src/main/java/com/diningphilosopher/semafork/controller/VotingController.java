package com.diningphilosopher.semafork.controller;

import com.diningphilosopher.semafork.dto.vote.CastVoteRequest;
import com.diningphilosopher.semafork.dto.vote.VoteResultResponse;
import com.diningphilosopher.semafork.service.VotingService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/parties/{partyId}")
public class VotingController {

    private final VotingService votingService;

    public VotingController(VotingService votingService) {
        this.votingService = votingService;
    }

    @PostMapping("/start-voting")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void startVoting(@PathVariable long partyId) {
        votingService.startVoting(partyId);
    }

    @PostMapping("/votes")
    @ResponseStatus(HttpStatus.CREATED)
    public void castVote(@PathVariable long partyId, @Valid @RequestBody CastVoteRequest request) {
        votingService.castVote(partyId, request);
    }

    @GetMapping
    public VoteResultResponse results(@PathVariable long partyId) {
        return votingService.getResults(partyId);
    }

}
