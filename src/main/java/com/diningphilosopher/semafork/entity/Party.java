package com.diningphilosopher.semafork.entity;

import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "party")
public class Party {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PartyStatus status;

    @Column(name = "join_code", nullable = false, unique = true, length = 8)
    private String joinCode;

    @Column(name = "host_token", nullable = false, unique = true, length = 64)
    private String hostToken;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @OneToMany(mappedBy = "party", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PartyMember> members = new ArrayList<>();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "winner_suggestion_id")
    private Suggestion winnerSuggestion;

    protected Party() {
    }

    public Party(String name, PartyStatus status, String joinCode, String hostToken, OffsetDateTime createdAt) {
        this.name = name;
        this.status = status;
        this.joinCode = joinCode;
        this.hostToken = hostToken;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public PartyStatus getStatus() {
        return status;
    }

    public String getJoinCode() {
        return joinCode;
    }

    public String getHostToken() {
        return hostToken;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public List<PartyMember> getMembers() {
        return members;
    }

    public Suggestion getWinnerSuggestion() {
        return winnerSuggestion;
    }

    public void setWinnerSuggestion(Suggestion winnerSuggestion) {
        this.winnerSuggestion = winnerSuggestion;
    }

    public void setStatus(PartyStatus status) {
        this.status = status;
    }
}
