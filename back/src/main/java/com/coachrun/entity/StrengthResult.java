package com.coachrun.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Retour de l'athlète sur une série réalisée d'un exercice de force (cf. DARI Lab).
 * Source du recalcul automatique du e1RM.
 */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "strength_results")
public class StrengthResult extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "scheduled_session_id", nullable = false)
    private ScheduledStrengthSession scheduledSession;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "athlete_id", nullable = false)
    private Athlete athlete;

    @Column(name = "exercise_id", nullable = false)
    private UUID exerciseId;

    @Column(name = "set_number", nullable = false)
    private int setNumber;

    @Column(name = "charge_kg", precision = 6, scale = 2)
    private BigDecimal chargeKg;

    @Column(name = "reps_done")
    private Integer repsDone;

    @Column(name = "duration_sec_done")
    private Integer durationSecDone;

    @Column(name = "rpe_done", precision = 3, scale = 1)
    private BigDecimal rpeDone;

    @Column(name = "rir_done")
    private Integer rirDone;

    @Column(name = "pain")
    private Integer pain;

    @Column(name = "comment", length = 1024)
    private String comment;
}
