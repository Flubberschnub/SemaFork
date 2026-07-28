package com.diningphilosopher.semafork.dto.party;

public record CreatePartyResponse(
        long partyId,
        String joinCode,
        String hostToken,
        long memberId,
        String memberToken
) {
}
