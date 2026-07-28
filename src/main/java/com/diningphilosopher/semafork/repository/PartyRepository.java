package com.diningphilosopher.semafork.repository;

import com.diningphilosopher.semafork.entity.Party;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PartyRepository extends JpaRepository<Party, Long> {
    Optional<Party> findByJoinCodeIgnoreCase(String joinCode);
    boolean existsByJoinCode(String joinCode);
}
