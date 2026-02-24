package com.diningphilosopher.semafork.repository;

import com.diningphilosopher.semafork.entity.Party;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PartyRepository extends JpaRepository<Party, Long> {
}
