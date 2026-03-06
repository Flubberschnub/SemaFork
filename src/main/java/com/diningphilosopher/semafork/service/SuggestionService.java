package com.diningphilosopher.semafork.service;

import com.diningphilosopher.semafork.dto.DtoMappers;
import com.diningphilosopher.semafork.dto.suggestion.CreateSuggestionRequest;
import com.diningphilosopher.semafork.dto.suggestion.SuggestionResponse;
import com.diningphilosopher.semafork.entity.Party;
import com.diningphilosopher.semafork.entity.PartyMember;
import com.diningphilosopher.semafork.entity.PartyStatus;
import com.diningphilosopher.semafork.entity.Suggestion;
import com.diningphilosopher.semafork.exception.BadRequestException;
import com.diningphilosopher.semafork.exception.ConflictException;
import com.diningphilosopher.semafork.exception.NotFoundException;
import com.diningphilosopher.semafork.repository.PartyMemberRepository;
import com.diningphilosopher.semafork.repository.PartyRepository;
import com.diningphilosopher.semafork.repository.SuggestionRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

@Service
public class SuggestionService {
    private final PartyRepository partyRepository;
    private final PartyMemberRepository partyMemberRepository;
    private final SuggestionRepository suggestionRepository;

    public SuggestionService(PartyRepository partyRepository,
                             PartyMemberRepository partyMemberRepository,
                             SuggestionRepository suggestionRepository) {
        this.partyRepository = partyRepository;
        this.partyMemberRepository = partyMemberRepository;
        this.suggestionRepository = suggestionRepository;
    }

    @Transactional
    public SuggestionResponse addSuggestion(long partyId, CreateSuggestionRequest request) {
        Party party = partyRepository.findById(partyId)
                .orElseThrow(() -> new NotFoundException("Party not found: " + partyId));

        if (party.getStatus() != PartyStatus.OPEN) {
            throw new BadRequestException("Party is not open");
        }

        PartyMember member = partyMemberRepository.findById(request.memberId())
                .orElseThrow(() -> new NotFoundException("Party member not found: " + request.memberId()));

        if (!member.getParty().getId().equals(partyId)) {
            throw new BadRequestException("Party member does not belong to the party");
        }

        Suggestion suggestion = new Suggestion(
                party,
                member,
                request.name().trim(),
                OffsetDateTime.now()
        );

        try {
            Suggestion saved = suggestionRepository.save(suggestion);
            return DtoMappers.toSuggestionResponse(saved);
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException("That suggestion already exists");
        }
    }

    @Transactional(readOnly = true)
    public List<SuggestionResponse> getSuggestions(long partyId) {
        if (!partyRepository.existsById(partyId)) {
            throw new NotFoundException("Party not found: " + partyId);
        }

        return suggestionRepository.findAllByPartyIdOrderByCreatedAtAsc(partyId)
                .stream()
                .map(DtoMappers::toSuggestionResponse)
                .toList();
    }
}
