package com.coachrun.entity;

import com.coachrun.entity.enums.AthleteOwnershipType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Relation coach référent ↔ athlète (cf. DARI Lab — multi-coach / club).
 * <ul>
 *   <li>{@code club == null} ⇒ athlète <strong>privé</strong> : visible du seul coach référent,
 *       jamais des autres coachs du club.</li>
 *   <li>{@code club != null} ⇒ athlète <strong>club</strong> : potentiellement visible par les
 *       autres coachs selon {@link AthleteCoachPermission} et le rôle club.</li>
 * </ul>
 * Le coach est un {@link User} (rôle coach), l'athlète une entité {@link Athlete}.
 */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "coach_athlete_relations",
        uniqueConstraints = @UniqueConstraint(name = "uq_coach_athlete", columnNames = {"coach_id", "athlete_id"}))
public class CoachAthleteRelation extends BaseEntity {

    /** {@code null} = athlète privé (rattaché au seul coach référent). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "club_id")
    private Club club;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "coach_id", nullable = false)
    private User coach;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "athlete_id", nullable = false)
    private Athlete athlete;

    /** Coach référent (responsable principal de la programmation de cet athlète). */
    @Column(name = "is_referent", nullable = false)
    private boolean referent = true;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    /** Déduit du rattachement club : {@code club == null} ⇒ {@code PRIVATE}. */
    @Transient
    public AthleteOwnershipType getOwnershipType() {
        return club == null ? AthleteOwnershipType.PRIVATE : AthleteOwnershipType.CLUB;
    }

    @Transient
    public boolean isPrivate() {
        return club == null;
    }
}
