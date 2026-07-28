package com.diningphilosopher.semafork.controller;

import com.diningphilosopher.semafork.dto.suggestion.CreateSuggestionRequest;
import com.diningphilosopher.semafork.dto.suggestion.SuggestionResponse;
import com.diningphilosopher.semafork.service.SuggestionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/parties/{partyId}/suggestions")
public class SuggestionController {
    private final SuggestionService suggestionService;

    public SuggestionController(SuggestionService suggestionService) {
        this.suggestionService = suggestionService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SuggestionResponse addSuggestion(
            @PathVariable long partyId,
            @RequestHeader("X-Member-Token") String memberToken,
            @Valid @RequestBody CreateSuggestionRequest request
    ) {
        return suggestionService.addSuggestion(partyId, memberToken, request);
    }

    @GetMapping
    public List<SuggestionResponse> getSuggestions(@PathVariable long partyId) {
        return suggestionService.getSuggestions(partyId);
    }
}
