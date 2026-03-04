package com.diningphilosopher.semafork.service;

import com.diningphilosopher.semafork.dto.*;
import com.diningphilosopher.semafork.dto.party.CreatePartyRequest;
import com.diningphilosopher.semafork.dto.party.JoinPartyRequest;
import com.diningphilosopher.semafork.dto.party.MemberResponse;
import com.diningphilosopher.semafork.dto.party.PartyResponse;
import com.diningphilosopher.semafork.entity.*;
import com.diningphilosopher.semafork.exception.BadRequestException;
import com.diningphilosopher.semafork.exception.ConflictException;
import com.diningphilosopher.semafork.exception.NotFoundException;
import com.diningphilosopher.semafork.repository.PartyMemberRepository;
import com.diningphilosopher.semafork.repository.PartyRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

@Service
public class PartyService {
    private final PartyRepository partyRepository;
    private final PartyMemberRepository partyMemberRepository;

    public PartyService(PartyRepository partyRepository, PartyMemberRepository partyMemberRepository) {
        this.partyRepository = partyRepository;
        this.partyMemberRepository = partyMemberRepository;
    }

    @Transactional
    public PartyResponse createParty(CreatePartyRequest request) {
        Party party = new Party(request.name().trim(), PartyStatus.OPEN, OffsetDateTime.now());
        Party saved = partyRepository.save(party);
        return DtoMappers.toPartyResponse(saved, List.of());
    }

    @Transactional
    public MemberResponse joinParty(long partyId, JoinPartyRequest request) {
        Party party = partyRepository.findById(partyId).orElseThrow(() -> new NotFoundException("Party not found: " + partyId));
        if (party.getStatus() != PartyStatus.OPEN) {
            throw new BadRequestException("Party is not open");
        }

        String memberName = request.memberName().trim();

        PartyMember member = new PartyMember(party, memberName, OffsetDateTime.now());

        try {
            PartyMember saved = partyMemberRepository.save(member);
            return DtoMappers.toMemberResponse(saved);
        } catch (DataIntegrityViolationException e) {
            // unique(party_id, member_name) triggered
            throw new ConflictException("Party member already exists");
        }
    }

    @Transactional(readOnly = true)
    public PartyResponse getParty(long partyId) {
        Party party = partyRepository.findById(partyId).orElseThrow(() -> new NotFoundException("Party not found: " + partyId));

        List<PartyMember> members = partyMemberRepository.findByPartyId(partyId);
        return DtoMappers.toPartyResponse(party, members);
    }
}
