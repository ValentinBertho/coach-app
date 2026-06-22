package com.coachrun.entity;

import com.coachrun.entity.enums.ClubRole;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Rattachement d'un coach ({@link User}) à un club avec son rôle club (cf. DARI Lab).
 * Distinct de {@code user_clubs} (simple appartenance multi-club) : porte le {@link ClubRole}
 * qui conditionne la visibilité par défaut des athlètes club et les capacités de gestion.
 */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "club_members",
        uniqueConstraints = @UniqueConstraint(name = "uq_club_member", columnNames = {"club_id", "coach_id"}))
public class ClubMember extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "club_id", nullable = false)
    private Club club;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "coach_id", nullable = false)
    private User coach;

    @Enumerated(EnumType.STRING)
    @Column(name = "club_role", nullable = false, length = 32)
    private ClubRole clubRole = ClubRole.COACH_ASSISTANT;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invited_by")
    private User invitedBy;

    @Column(name = "active", nullable = false)
    private boolean active = true;
}
