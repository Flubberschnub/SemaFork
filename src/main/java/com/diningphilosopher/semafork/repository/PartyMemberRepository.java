package com.diningphilosopher.semafork.repository;

import com.diningphilosopher.semafork.entity.PartyMember;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PartyMemberRepository extends JpaRepository<PartyMember, Long> {
    List<PartyMember> findByPartyIdOrderByJoinedAtAsc(Long partyId);
    Optional<PartyMember> findByPartyIdAndMemberToken(Long partyId, String memberToken);
    long countByPartyId(Long partyId);
}
