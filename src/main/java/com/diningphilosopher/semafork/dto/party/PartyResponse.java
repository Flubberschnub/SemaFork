package com.diningphilosopher.semafork.dto.party;

import java.util.List;

public record PartyResponse(
        long id,
        String name,
        String status,
        String joinCode,
        List<MemberResponse> members
) {
}
