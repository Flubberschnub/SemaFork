package com.diningphilosopher.semafork.dto.party;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record JoinPartyRequest(
        @NotBlank @Size(max = 80) String memberName
) {
}
