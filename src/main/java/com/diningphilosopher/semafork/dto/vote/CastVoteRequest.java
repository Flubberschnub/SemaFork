package com.diningphilosopher.semafork.dto.vote;

import jakarta.validation.constraints.NotNull;

public record CastVoteRequest(
        @NotNull Long memberId,
        @NotNull Long suggestionId
) {
}
