package com.diningphilosopher.semafork.service;

import com.diningphilosopher.semafork.dto.vote.CastVoteRequest;
import com.diningphilosopher.semafork.dto.vote.VoteResultResponse;
import com.diningphilosopher.semafork.entity.*;
import com.diningphilosopher.semafork.exception.BadRequestException;
import com.diningphilosopher.semafork.exception.ConflictException;
import com.diningphilosopher.semafork.exception.NotFoundException;
import com.diningphilosopher.semafork.exception.UnauthorizedException;
import com.diningphilosopher.semafork.repository.PartyMemberRepository;
import com.diningphilosopher.semafork.repository.PartyRepository;
import com.diningphilosopher.semafork.repository.SuggestionRepository;
import com.diningphilosopher.semafork.repository.VoteRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class VotingService {

    private final PartyRepository partyRepository;
    private final PartyMemberRepository partyMemberRepository;
    private final SuggestionRepository suggestionRepository;
    private final VoteRepository voteRepository;

    public VotingService(
            PartyRepository partyRepository,
            PartyMemberRepository partyMemberRepository,
            SuggestionRepository suggestionRepository,
            VoteRepository voteRepository
    ) {
        this.partyRepository = partyRepository;
        this.partyMemberRepository = partyMemberRepository;
        this.suggestionRepository = suggestionRepository;
        this.voteRepository = voteRepository;
    }

    @Transactional
    public void startVoting(long partyId, String hostToken) {
        Party party = requireParty(partyId);
        requireHost(party, hostToken);

        if (party.getStatus() != PartyStatus.OPEN) {
            throw new BadRequestException("Party is not open for suggestions");
        }
        if (suggestionRepository.countByPartyId(partyId) < 2) {
            throw new BadRequestException("Add at least two suggestions before voting");
        }

        party.setStatus(PartyStatus.VOTING);
    }

    @Transactional
    public void castVote(long partyId, String memberToken, CastVoteRequest request) {
        Party party = requireParty(partyId);
        if (party.getStatus() != PartyStatus.VOTING) {
            throw new BadRequestException("Party is not in the voting phase");
        }

        PartyMember member = partyMemberRepository.findByPartyIdAndMemberToken(partyId, memberToken)
                .orElseThrow(() -> new UnauthorizedException("Invalid participant session"));

        Suggestion suggestion = suggestionRepository.findById(request.suggestionId())
                .orElseThrow(() -> new NotFoundException("Suggestion not found"));
        if (!suggestion.getParty().getId().equals(partyId)) {
            throw new BadRequestException("Suggestion does not belong to this party");
        }

        if (voteRepository.existsByPartyIdAndMemberId(partyId, member.getId())) {
            throw new ConflictException("You have already voted");
        }

        try {
            voteRepository.saveAndFlush(new Vote(party, member, suggestion, OffsetDateTime.now()));
        } catch (DataIntegrityViolationException ex) {
            throw new ConflictException("You have already voted");
        }

        long memberCount = partyMemberRepository.countByPartyId(partyId);
        long voteCount = voteRepository.countByPartyId(partyId);
        if (memberCount > 0 && voteCount >= memberCount) {
            finalizeWinner(party);
        }
    }

    @Transactional
    public void finalizeVoting(long partyId, String hostToken) {
        Party party = requireParty(partyId);
        requireHost(party, hostToken);

        if (party.getStatus() != PartyStatus.VOTING) {
            throw new BadRequestException("Party is not in the voting phase");
        }
        if (voteRepository.countByPartyId(partyId) == 0) {
            throw new BadRequestException("At least one vote is required");
        }

        finalizeWinner(party);
    }

    private void finalizeWinner(Party party) {
        List<Object[]> counts = voteRepository.countVotesBySuggestions(party.getId());
        if (counts.isEmpty()) {
            throw new BadRequestException("At least one vote is required");
        }

        long bestCount = ((Number) counts.getFirst()[1]).longValue();
        List<Long> tiedSuggestionIds = new ArrayList<>();
        for (Object[] row : counts) {
            long count = ((Number) row[1]).longValue();
            if (count != bestCount) {
                break;
            }
            tiedSuggestionIds.add(((Number) row[0]).longValue());
        }

        long winnerId = tiedSuggestionIds.get(
                ThreadLocalRandom.current().nextInt(tiedSuggestionIds.size())
        );
        Suggestion winner = suggestionRepository.findById(winnerId)
                .orElseThrow(() -> new NotFoundException("Winning suggestion not found"));

        party.setWinnerSuggestion(winner);
        party.setStatus(PartyStatus.FINALIZED);
    }

    @Transactional(readOnly = true)
    public VoteResultResponse getResults(long partyId) {
        Party party = requireParty(partyId);
        Map<Long, Long> counts = new LinkedHashMap<>();

        if (party.getStatus() == PartyStatus.FINALIZED) {
            for (Object[] row : voteRepository.countVotesBySuggestions(partyId)) {
                counts.put(((Number) row[0]).longValue(), ((Number) row[1]).longValue());
            }
        }

        Suggestion winner = party.getWinnerSuggestion();
        return new VoteResultResponse(
                party.getId(),
                party.getStatus().name(),
                winner == null ? null : winner.getId(),
                winner == null ? null : winner.getName(),
                counts
        );
    }

    private Party requireParty(long partyId) {
        return partyRepository.findById(partyId)
                .orElseThrow(() -> new NotFoundException("Party not found"));
    }

    private void requireHost(Party party, String hostToken) {
        if (!party.getHostToken().equals(hostToken)) {
            throw new UnauthorizedException("Invalid host session");
        }
    }
}
