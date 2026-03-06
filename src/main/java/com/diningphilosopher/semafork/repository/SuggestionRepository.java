package com.diningphilosopher.semafork.repository;

import com.diningphilosopher.semafork.entity.Suggestion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SuggestionRepository extends JpaRepository<Suggestion, Long> {
    List<Suggestion> findAllByPartyIdOrderByCreatedAtAsc(Long partyId);
    List<Suggestion> findAllByMemberIdOrderByCreatedAtAsc(Long memberId);
}
