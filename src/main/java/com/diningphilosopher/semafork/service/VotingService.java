package com.diningphilosopher.semafork.service;

import com.diningphilosopher.semafork.dto.vote.CastVoteRequest;
import com.diningphilosopher.semafork.dto.vote.VoteResultResponse;
import com.diningphilosopher.semafork.entity.*;
import com.diningphilosopher.semafork.exception.BadRequestException;
import com.diningphilosopher.semafork.exception.NotFoundException;
import com.diningphilosopher.semafork.repository.PartyMemberRepository;
import com.diningphilosopher.semafork.repository.PartyRepository;
import com.diningphilosopher.semafork.repository.SuggestionRepository;
import com.diningphilosopher.semafork.repository.VoteRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class VotingService {

    private final PartyRepository partyRepository;
    private final PartyMemberRepository partyMemberRepository;
    private final SuggestionRepository suggestionRepository;
    private final VoteRepository voteRepository;

    private final int maxVotesPerMember = 1;

    public VotingService(PartyRepository partyRepository,
                         PartyMemberRepository partyMemberRepository,
                         SuggestionRepository suggestionRepository,
                         VoteRepository voteRepository) {
        this.partyRepository = partyRepository;
        this.partyMemberRepository = partyMemberRepository;
        this.suggestionRepository = suggestionRepository;
        this.voteRepository = voteRepository;
    }

    @Transactional
    public void startVoting(long partyId) {
        Party party = partyRepository.findById(partyId)
                .orElseThrow(() -> new NotFoundException("Party not found: " + partyId));

        if (party.getStatus() != PartyStatus.OPEN) {
            throw new BadRequestException("Party is not open");
        }

        party.setStatus(PartyStatus.VOTING);
    }

    @Transactional
    public void castVote(long partyId, CastVoteRequest request) {
        Party party = partyRepository.findById(partyId)
                .orElseThrow(() -> new NotFoundException("Party not found: " + partyId));

        if (party.getStatus() != PartyStatus.VOTING) {
            throw new BadRequestException("Party is not in voting phase");
        }

        PartyMember member = partyMemberRepository.findById(request.memberId())
                .orElseThrow(() -> new NotFoundException("Party member not found: " + request.memberId()));

        if (!(member.getParty().getId().equals(party.getId()))) {
            throw new BadRequestException("Party member does not belong to the party");
        }

        Suggestion suggestion = suggestionRepository.findById(request.suggestionId())
                .orElseThrow(() -> new NotFoundException("Suggestion not found: " + request.suggestionId()));

        if (!suggestion.getParty().getId().equals(party.getId())) {
            throw new BadRequestException("Suggestion does not belong to the party");
        }

        long memberVotes = voteRepository.countByPartyIdAndMemberId(partyId, member.getId());
        if (memberVotes >= maxVotesPerMember) {
            throw new BadRequestException("Member has already voted");
        }

        Vote vote = new Vote(party, member, suggestion, OffsetDateTime.now());

        voteRepository.save(vote);

        long memberCount = party.getMembers().size();
        long voteCount = voteRepository.countByPartyId(partyId);

        if (memberCount > 0 && voteCount >= memberCount) {
            finalizeWinner(party);
        }
    }

    private void finalizeWinner(Party party) {
        List<Object[]> counts = voteRepository.countVotesBySuggestions(party.getId());

        if (counts.isEmpty()) {
            throw new BadRequestException("No votes found");
        }

        // Winner = top count, Tie break: lowest suggestionId (simple determinism for now)
        long bestSuggestionId = -1;
        long bestCount = -1;
        for (Object[] row : counts) {
            long suggestionId = (Long) row[0];
            long count = (Long) row[1];
            if (count > bestCount || (count == bestCount && suggestionId < bestSuggestionId)) {
                bestCount = count;
                bestSuggestionId = suggestionId;
            }
        }

        Suggestion winner = suggestionRepository.findById(bestSuggestionId)
                .orElseThrow(() -> new NotFoundException("Winner suggestion not found"));

        party.setWinnerSuggestion(winner);
        party.setStatus(PartyStatus.FINALIZED);
    }

    @Transactional(readOnly = true)
    public VoteResultResponse getResults(long partyId) {
        Party party = partyRepository.findById(partyId)
                .orElseThrow(() -> new NotFoundException("Party not found: " + partyId));

        Map<Long, Long> countsMap = new LinkedHashMap<>();
        for (Object[] row : voteRepository.countVotesBySuggestions(partyId)) {
            countsMap.put((Long) row[0], (Long) row[1]);
        }

        Suggestion winner = party.getWinnerSuggestion();
        return new VoteResultResponse(
                party.getId(),
                party.getStatus().name(),
                winner == null ? null : winner.getId(),
                winner == null ? null : winner.getName(),
                countsMap
        );
    }

}
