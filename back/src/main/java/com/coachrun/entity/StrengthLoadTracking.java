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
import java.time.LocalDate;
import java.util.UUID;

/**
 * Charge interne d'une séance de force réalisée, en unités arbitraires (UA), cf. DARI Lab §6.6.
 * Alimente les courbes de charge mécanique/métabolique de l'onglet Suivi &amp; Analyse.
 */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "strength_load_tracking")
public class StrengthLoadTracking extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "athlete_id", nullable = false)
    private Athlete athlete;

    @Column(name = "scheduled_session_id")
    private UUID scheduledSessionId;

    @Column(name = "session_date", nullable = false)
    private LocalDate sessionDate;

    @Column(name = "mechanical_load", precision = 10, scale = 1, nullable = false)
    private BigDecimal mechanicalLoad;

    @Column(name = "metabolic_load", precision = 10, scale = 1, nullable = false)
    private BigDecimal metabolicLoad;
}
