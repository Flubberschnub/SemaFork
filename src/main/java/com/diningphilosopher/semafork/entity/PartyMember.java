package com.diningphilosopher.semafork.entity;

import jakarta.persistence.*;

import java.time.OffsetDateTime;

@Entity
@Table(
        name = "party_member",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_party_member_partyid_membername", columnNames = {"party_id", "member_name"})
        }
)

public class PartyMember {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "party_id", nullable = false)
    private Party party;

    @Column(nullable = false)
    private String memberName;

    @Column(nullable = false)
    private OffsetDateTime joinedAt;

    protected PartyMember() {
    }

    public PartyMember(Party party, String memberName, OffsetDateTime joinedAt) {
        this.party = party;
        this.memberName = memberName;
        this.joinedAt = joinedAt;
    }

    public Long getId() {
        return id;
    }

    public Party getParty() {
        return party;
    }

    public String getMemberName() {
        return memberName;
    }

    public OffsetDateTime getJoinedAt() {
        return joinedAt;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public void setParty(Party party) {
        this.party = party;
    }

    public void setMemberName(String memberName) {
        this.memberName = memberName;
    }

    public void setJoinedAt(OffsetDateTime joinedAt) {
        this.joinedAt = joinedAt;
    }

}