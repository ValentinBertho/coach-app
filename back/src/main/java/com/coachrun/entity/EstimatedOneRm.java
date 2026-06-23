package com.coachrun.entity;

import com.coachrun.entity.enums.RmFormula;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
 * Historique du e1RM calculé par exercice et par athlète (cf. DARI Lab) : alimente la courbe
 * d'évolution de la force.
 */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "estimated_1rm")
public class EstimatedOneRm extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "athlete_id", nullable = false)
    private Athlete athlete;

    @Column(name = "exercise_id", nullable = false)
    private UUID exerciseId;

    @Column(name = "source_result_id")
    private UUID sourceResultId;

    @Column(name = "charge_kg", precision = 6, scale = 2, nullable = false)
    private BigDecimal chargeKg;

    @Column(name = "reps", nullable = false)
    private int reps;

    @Column(name = "rpe_or_rir", length = 16)
    private String rpeOrRir;

    @Enumerated(EnumType.STRING)
    @Column(name = "formula_used", nullable = false, length = 16)
    private RmFormula formulaUsed;

    @Column(name = "e1rm_kg", precision = 6, scale = 2, nullable = false)
    private BigDecimal e1rmKg;
}
