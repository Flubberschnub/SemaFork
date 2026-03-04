package com.diningphilosopher.semafork.dto.party;

import java.util.List;

public record PartyResponse(
        long id,
        String name,
        String status,
        List<MemberResponse> members
) {
}
