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

import java.time.Instant;
import java.util.UUID;

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

    /**
     * {@code true} tant que le coach suit l'athlète.
     *
     * <p>Ce booléen existait depuis l'origine sans que rien ne le mette jamais à {@code false} :
     * la fin d'une relation n'était pas implémentée. Il ne suffisait d'ailleurs pas — le passer à
     * {@code false} ne retirait aucun accès, parce que {@code AthleteAccessValidator} retombait
     * alors sur l'accès club, qui rendait l'écriture au coach propriétaire du club portant la
     * fiche. Voir la garde de {@code effectiveLevel} : c'est l'existence d'une relation référente,
     * active ou close, qui distingue désormais « jamais suivi » de « n'est plus suivi ».</p>
     */
    @Column(name = "active", nullable = false)
    private boolean active = true;

    /**
     * Date de fin de la relation ; {@code null} tant qu'elle court.
     *
     * <p>Redondant avec {@link #active}, et volontairement : le booléen dit l'état, l'horodatage
     * dit quand — et « depuis quand ce coach n'a-t-il plus accès à cet athlète ? » est exactement
     * la question qu'on pose après coup, sans pouvoir y répondre avec un booléen seul.</p>
     */
    @Column(name = "ended_at")
    private Instant endedAt;

    /** Qui a mis fin à la relation : le coach, l'athlète, ou un administrateur. */
    @Column(name = "ended_by_user_id")
    private UUID endedByUserId;

    /** Motif facultatif, jamais publié — il sert au support, pas à l'autre partie. */
    @Column(name = "end_reason", length = 500)
    private String endReason;

    /** Fin annoncée par le coach (préavis) ; {@code null} si aucune fin n'est prévue. */
    @Column(name = "planned_end_at")
    private Instant plannedEndAt;

    @Column(name = "planned_end_by")
    private UUID plannedEndBy;

    /** Le mot du coach qui accompagne l'annonce ; transmis à l'athlète. */
    @Column(name = "planned_end_reason", length = 500)
    private String plannedEndReason;

    /** Le coach laisse les séances à venir sur la fiche à la fin (par défaut, elles sont retirées). */
    @Column(name = "keep_future_workouts", nullable = false)
    private boolean keepFutureWorkouts = false;

    @Column(name = "end_reminder_sent_at")
    private Instant endReminderSentAt;

    /**
     * Clôt la relation. Idempotent : reclore une relation déjà close ne réécrit ni la date ni
     * l'auteur, une seconde clôture n'étant pas un événement.
     *
     * @param byUserId auteur de la clôture ({@code null} si le système)
     * @param reason   motif facultatif
     */
    public void end(UUID byUserId, String reason) {
        if (!active) {
            return;
        }
        active = false;
        endedAt = Instant.now();
        endedByUserId = byUserId;
        endReason = reason;
    }

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
