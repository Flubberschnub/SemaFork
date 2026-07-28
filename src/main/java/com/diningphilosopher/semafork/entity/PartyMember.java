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

    @Column(name = "member_name", nullable = false)
    private String memberName;

    @Column(name = "member_token", nullable = false, unique = true, length = 64)
    private String memberToken;

    @Column(name = "joined_at", nullable = false)
    private OffsetDateTime joinedAt;

    protected PartyMember() {
    }

    public PartyMember(Party party, String memberName, String memberToken, OffsetDateTime joinedAt) {
        this.party = party;
        this.memberName = memberName;
        this.memberToken = memberToken;
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

    public String getMemberToken() {
        return memberToken;
    }

    public OffsetDateTime getJoinedAt() {
        return joinedAt;
    }
}
