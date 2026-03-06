package com.diningphilosopher.semafork.dto.suggestion;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateSuggestionRequest(
        @NotNull Long memberId,
        @NotBlank @Size(max = 120) String name
) {
}
