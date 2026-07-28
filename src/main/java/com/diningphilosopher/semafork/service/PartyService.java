package com.diningphilosopher.semafork.service;

import com.diningphilosopher.semafork.dto.DtoMappers;
import com.diningphilosopher.semafork.dto.party.*;
import com.diningphilosopher.semafork.entity.Party;
import com.diningphilosopher.semafork.entity.PartyMember;
import com.diningphilosopher.semafork.entity.PartyStatus;
import com.diningphilosopher.semafork.exception.BadRequestException;
import com.diningphilosopher.semafork.exception.ConflictException;
import com.diningphilosopher.semafork.exception.NotFoundException;
import com.diningphilosopher.semafork.repository.PartyMemberRepository;
import com.diningphilosopher.semafork.repository.PartyRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class PartyService {
    private static final String JOIN_CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int JOIN_CODE_LENGTH = 6;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final PartyRepository partyRepository;
    private final PartyMemberRepository partyMemberRepository;

    public PartyService(PartyRepository partyRepository, PartyMemberRepository partyMemberRepository) {
        this.partyRepository = partyRepository;
        this.partyMemberRepository = partyMemberRepository;
    }

    @Transactional
    public CreatePartyResponse createParty(CreatePartyRequest request) {
        String joinCode = generateUniqueJoinCode();
        String hostToken = generateToken();
        String memberToken = generateToken();
        OffsetDateTime now = OffsetDateTime.now();

        Party party = partyRepository.save(new Party(
                request.name().trim(),
                PartyStatus.OPEN,
                joinCode,
                hostToken,
                now
        ));

        PartyMember host = partyMemberRepository.save(new PartyMember(
                party,
                request.hostName().trim(),
                memberToken,
                now
        ));

        return new CreatePartyResponse(
                party.getId(),
                party.getJoinCode(),
                hostToken,
                host.getId(),
                memberToken
        );
    }

    @Transactional
    public JoinPartyResponse joinParty(JoinPartyRequest request) {
        String normalizedCode = request.joinCode().trim().toUpperCase(Locale.ROOT);
        Party party = partyRepository.findByJoinCodeIgnoreCase(normalizedCode)
                .orElseThrow(() -> new NotFoundException("Party not found"));

        if (party.getStatus() != PartyStatus.OPEN) {
            throw new BadRequestException("This party is no longer accepting members");
        }

        try {
            PartyMember member = partyMemberRepository.save(new PartyMember(
                    party,
                    request.memberName().trim(),
                    generateToken(),
                    OffsetDateTime.now()
            ));

            return new JoinPartyResponse(
                    party.getId(),
                    party.getJoinCode(),
                    member.getId(),
                    member.getMemberToken()
            );
        } catch (DataIntegrityViolationException ex) {
            throw new ConflictException("That name is already in this party");
        }
    }

    @Transactional(readOnly = true)
    public PartyResponse getParty(long partyId) {
        Party party = partyRepository.findById(partyId)
                .orElseThrow(() -> new NotFoundException("Party not found"));
        return toResponse(party);
    }

    @Transactional(readOnly = true)
    public PartyResponse getPartyByJoinCode(String joinCode) {
        Party party = partyRepository.findByJoinCodeIgnoreCase(joinCode.trim())
                .orElseThrow(() -> new NotFoundException("Party not found"));
        return toResponse(party);
    }

    private PartyResponse toResponse(Party party) {
        List<PartyMember> members = partyMemberRepository.findByPartyIdOrderByJoinedAtAsc(party.getId());
        return DtoMappers.toPartyResponse(party, members);
    }

    private String generateUniqueJoinCode() {
        for (int attempt = 0; attempt < 20; attempt++) {
            StringBuilder code = new StringBuilder(JOIN_CODE_LENGTH);
            for (int i = 0; i < JOIN_CODE_LENGTH; i++) {
                code.append(JOIN_CODE_ALPHABET.charAt(RANDOM.nextInt(JOIN_CODE_ALPHABET.length())));
            }
            if (!partyRepository.existsByJoinCode(code.toString())) {
                return code.toString();
            }
        }
        throw new IllegalStateException("Could not generate a party code");
    }

    private String generateToken() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
