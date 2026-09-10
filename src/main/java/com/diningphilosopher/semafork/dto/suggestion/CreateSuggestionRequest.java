package com.diningphilosopher.semafork.dto.suggestion;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateSuggestionRequest(
        @Size(max = 120) String name,
        @Size(max = 255) @Pattern(regexp = "[A-Za-z0-9_-]+") String googlePlaceId
) {
    @AssertTrue(message = "Provide either a restaurant name or a Google Place ID, not both")
    public boolean isValidSource() {
        boolean hasName = name != null && !name.isBlank();
        return hasName != (googlePlaceId != null && !googlePlaceId.isBlank());
    }
}
