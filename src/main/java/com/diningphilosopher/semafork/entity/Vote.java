package com.diningphilosopher.semafork.entity;

import jakarta.persistence.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "vote")
public class Vote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "party_id", nullable = false)
    private Party party;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private PartyMember member;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "suggestion_id", nullable = false)
    private Suggestion suggestion;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected Vote() {
    }

    public Vote(Party party, PartyMember member, Suggestion suggestion, OffsetDateTime createdAt) {
        this.party = party;
        this.member = member;
        this.suggestion = suggestion;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public Party getParty() {
        return party;
    }

    public PartyMember getMember() {
        return member;
    }

    public Suggestion getSuggestion() {
        return suggestion;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setParty(Party party) {
        this.party = party;
    }

    public void setMember(PartyMember member) {
        this.member = member;
    }

    public void setSuggestion(Suggestion suggestion) {
        this.suggestion = suggestion;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

}
