package com.coachrun.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * 1RM courant d'un athlète pour un exercice (base de calcul des charges %RM, cf. DARI Lab).
 * {@code source} : 'estimated' | 'tested' | 'manual' — un test direct prévaut sur une estimation.
 */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "athlete_1rm_profile",
        uniqueConstraints = @UniqueConstraint(name = "uq_athlete_1rm", columnNames = {"athlete_id", "exercise_id"}))
public class Athlete1rmProfile extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "athlete_id", nullable = false)
    private Athlete athlete;

    @Column(name = "exercise_id", nullable = false)
    private UUID exerciseId;

    @Column(name = "rm_kg", precision = 6, scale = 2, nullable = false)
    private BigDecimal rmKg;

    @Column(name = "source", length = 16, nullable = false)
    private String source = "manual";
}
