package com.diningphilosopher.semafork.entity;

import jakarta.persistence.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "suggestion")
public class Suggestion {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "party_id", nullable = false)
    private Party party;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private PartyMember member;

    // A manual label only; null for Google Maps candidates.
    @Column
    private String name;

    @Column(name = "google_place_id", length = 255)
    private String googlePlaceId;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected Suggestion() {
    }

    public Suggestion(Party party, PartyMember member, String name, OffsetDateTime createdAt) {
        this.party = party;
        this.member = member;
        this.name = name;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public Party getParty() { return party; }
    public PartyMember getMember() { return member; }
    public String getName() { return name; }
    public String getGooglePlaceId() { return googlePlaceId; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setParty(Party party) { this.party = party; }
    public void setMember(PartyMember member) { this.member = member; }
    public void setName(String name) { this.name = name; }
    public void setGooglePlaceId(String googlePlaceId) { this.googlePlaceId = googlePlaceId; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
