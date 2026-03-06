package com.diningphilosopher.semafork.dto.vote;

import java.util.Map;

public record VoteResultResponse(
        long partyId,
        String status,
        Long winnerSuggestionId,
        String winnerSuggestionName,
        Map<Long, Long> counts
) {
}
