package com.coachrun.entity;

import com.coachrun.entity.enums.StrengthTestProtocol;
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
import java.time.LocalDate;
import java.util.UUID;

/**
 * Test de force daté d'un athlète pour un exercice (cf. DARI Lab §6.5). Un test direct alimente
 * le profil 1RM avec la source {@code tested}, qui prévaut sur toute estimation.
 */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "strength_test")
public class StrengthTest extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "athlete_id", nullable = false)
    private Athlete athlete;

    @Column(name = "exercise_id", nullable = false)
    private UUID exerciseId;

    @Enumerated(EnumType.STRING)
    @Column(name = "protocol", nullable = false, length = 24)
    private StrengthTestProtocol protocol;

    @Column(name = "test_date", nullable = false)
    private LocalDate testDate;

    @Column(name = "weight_kg", precision = 6, scale = 2)
    private BigDecimal weightKg;

    @Column(name = "reps")
    private Integer reps;

    @Column(name = "duration_sec")
    private Integer durationSec;

    @Column(name = "rir")
    private Integer rir;

    @Column(name = "computed_e1rm_kg", precision = 6, scale = 2, nullable = false)
    private BigDecimal computedE1rmKg;

    @Column(name = "notes", length = 1024)
    private String notes;
}
