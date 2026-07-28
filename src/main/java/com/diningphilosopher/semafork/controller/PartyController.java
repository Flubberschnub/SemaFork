package com.diningphilosopher.semafork.controller;

import com.diningphilosopher.semafork.dto.party.*;
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

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CreatePartyResponse createParty(@Valid @RequestBody CreatePartyRequest request) {
        return partyService.createParty(request);
    }

    @PostMapping("/join")
    @ResponseStatus(HttpStatus.CREATED)
    public JoinPartyResponse joinParty(@Valid @RequestBody JoinPartyRequest request) {
        return partyService.joinParty(request);
    }

    @GetMapping("/code/{joinCode}")
    public PartyResponse getPartyByJoinCode(@PathVariable String joinCode) {
        return partyService.getPartyByJoinCode(joinCode);
    }

    @GetMapping("/{partyId}")
    public PartyResponse getParty(@PathVariable long partyId) {
        return partyService.getParty(partyId);
    }
}
