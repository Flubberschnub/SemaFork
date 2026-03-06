package com.diningphilosopher.semafork.dto;

import com.diningphilosopher.semafork.dto.party.MemberResponse;
import com.diningphilosopher.semafork.dto.party.PartyResponse;
import com.diningphilosopher.semafork.dto.suggestion.SuggestionResponse;
import com.diningphilosopher.semafork.entity.Party;
import com.diningphilosopher.semafork.entity.PartyMember;
import com.diningphilosopher.semafork.entity.Suggestion;

import java.util.List;

public class DtoMappers {
    private DtoMappers() {
    }

    public static MemberResponse toMemberResponse(PartyMember m) {
        return new MemberResponse(m.getId(), m.getMemberName());
    }

    public static PartyResponse toPartyResponse(Party p, List<PartyMember> members) {
        return new PartyResponse(p.getId(), p.getName(), p.getStatus().name(), members.stream()
                .map(DtoMappers::toMemberResponse)
                .toList());
    }

    public static SuggestionResponse toSuggestionResponse(Suggestion s) {
        return new SuggestionResponse(s.getId(), s.getMember().getId(), s.getMember().getMemberName(), s.getName());
    }
}
