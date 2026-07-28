package com.diningphilosopher.semafork.dto.party;

public record JoinPartyResponse(
        long partyId,
        String joinCode,
        long memberId,
        String memberToken
) {
}
