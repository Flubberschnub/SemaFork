package com.diningphilosopher.semafork.dto.suggestion;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateSuggestionRequest(
        @NotBlank @Size(max = 120) String name
) {
}
