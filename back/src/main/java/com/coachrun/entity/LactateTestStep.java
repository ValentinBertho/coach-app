package com.coachrun.entity;

import com.coachrun.security.EncryptedBigDecimalConverter;
import com.coachrun.security.EncryptedIntegerConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Palier d'un test lactate : vitesse (donnée de performance, en clair), lactate et FC
 * (données de santé, chiffrées au repos).
 */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "lactate_test_steps")
public class LactateTestStep extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "test_id", nullable = false)
    private LactateTest test;

    @Column(name = "step_order", nullable = false)
    private int stepOrder;

    @Column(name = "speed_ms", precision = 5, scale = 2, nullable = false)
    private BigDecimal speedMs;

    @Convert(converter = EncryptedIntegerConverter.class)
    @Column(name = "hr")
    private Integer hr;

    @Convert(converter = EncryptedBigDecimalConverter.class)
    @Column(name = "lactate")
    private BigDecimal lactate;

    @Column(name = "rpe")
    private Integer rpe;

    @Column(name = "duration_s")
    private Integer durationS;
}
