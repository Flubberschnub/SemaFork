package com.diningphilosopher.semafork.controller;

import com.diningphilosopher.semafork.dto.party.CreatePartyRequest;
import com.diningphilosopher.semafork.dto.party.JoinPartyRequest;
import com.diningphilosopher.semafork.dto.party.MemberResponse;
import com.diningphilosopher.semafork.dto.party.PartyResponse;
import com.diningphilosopher.semafork.service.PartyService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/parties")
public class PartyController {
    private final PartyService partyService;

    public PartyController(PartyService partyService) {
        this.partyService = partyService;
    }

    // Create a party
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PartyResponse createParty(@Valid @RequestBody CreatePartyRequest request) {
        return partyService.createParty(request);
    }

    // Join a party
    @PostMapping("/{partyId}/members")
    @ResponseStatus(HttpStatus.CREATED)
    public MemberResponse joinParty(
            @PathVariable long partyId,
            @Valid @RequestBody JoinPartyRequest request
    ) {
        return partyService.joinParty(partyId, request);
    }

    // Get party details
    @GetMapping("/{partyId}")
    public PartyResponse getParty(@PathVariable long partyId) {
        return partyService.getParty(partyId);
    }
}