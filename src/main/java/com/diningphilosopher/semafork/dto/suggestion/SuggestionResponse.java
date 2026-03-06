package com.diningphilosopher.semafork.dto.suggestion;

public record SuggestionResponse(
        long id,
        long memberId,
        String memberName,
        String name
) {
}
