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

    public static MemberResponse toMemberResponse(PartyMember member) {
        return new MemberResponse(member.getId(), member.getMemberName());
    }

    public static PartyResponse toPartyResponse(Party party, List<PartyMember> members) {
        return new PartyResponse(
                party.getId(),
                party.getName(),
                party.getStatus().name(),
                party.getJoinCode(),
                members.stream().map(DtoMappers::toMemberResponse).toList()
        );
    }

    public static SuggestionResponse toSuggestionResponse(Suggestion suggestion) {
        return new SuggestionResponse(
                suggestion.getId(),
                suggestion.getMember().getId(),
                suggestion.getMember().getMemberName(),
                suggestion.getName()
        );
    }
}
