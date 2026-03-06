package com.diningphilosopher.semafork.repository;

import com.diningphilosopher.semafork.entity.Vote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface VoteRepository extends JpaRepository<Vote, Long> {
    boolean existsByPartyIdAndMemberId(Long partyId, Long memberId);
    long countByPartyId(Long partyId);

    @Query("""
        SELECT v.suggestion.id as suggestionId, COUNT(v.id) as count
        FROM Vote v
        WHERE v.party.id = :partyId
        GROUP BY v.suggestion.id
        ORDER BY count DESC
    """)
    List<Object[]> countVotesBySuggestions(@Param("partyId") Long partyId);

    long countByPartyIdAndMemberId(Long partyId, Long memberId);
}
