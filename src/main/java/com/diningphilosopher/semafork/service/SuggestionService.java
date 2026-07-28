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
import com.diningphilosopher.semafork.exception.UnauthorizedException;
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

    public SuggestionService(
            PartyRepository partyRepository,
            PartyMemberRepository partyMemberRepository,
            SuggestionRepository suggestionRepository
    ) {
        this.partyRepository = partyRepository;
        this.partyMemberRepository = partyMemberRepository;
        this.suggestionRepository = suggestionRepository;
    }

    @Transactional
    public SuggestionResponse addSuggestion(
            long partyId,
            String memberToken,
            CreateSuggestionRequest request
    ) {
        Party party = partyRepository.findById(partyId)
                .orElseThrow(() -> new NotFoundException("Party not found"));

        if (party.getStatus() != PartyStatus.OPEN) {
            throw new BadRequestException("Suggestions are closed");
        }

        PartyMember member = partyMemberRepository.findByPartyIdAndMemberToken(partyId, memberToken)
                .orElseThrow(() -> new UnauthorizedException("Invalid participant session"));

        Suggestion suggestion = new Suggestion(
                party,
                member,
                request.name().trim(),
                OffsetDateTime.now()
        );

        try {
            return DtoMappers.toSuggestionResponse(suggestionRepository.saveAndFlush(suggestion));
        } catch (DataIntegrityViolationException ex) {
            throw new ConflictException("That suggestion is already in this party");
        }
    }

    @Transactional(readOnly = true)
    public List<SuggestionResponse> getSuggestions(long partyId) {
        if (!partyRepository.existsById(partyId)) {
            throw new NotFoundException("Party not found");
        }

        return suggestionRepository.findAllByPartyIdOrderByCreatedAtAsc(partyId)
                .stream()
                .map(DtoMappers::toSuggestionResponse)
                .toList();
    }
}
