package com.diningphilosopher.semafork.repository;

import com.diningphilosopher.semafork.entity.PartyMember;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PartyMemberRepository extends JpaRepository<PartyMember, Long> {
    List<PartyMember> findByPartyId(Long partyId);
    boolean existsByPartyIdAndMemberName(Long partyId, String memberName);
}
