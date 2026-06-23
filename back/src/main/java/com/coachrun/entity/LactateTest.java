package com.coachrun.entity;

import com.coachrun.entity.enums.TestType;
import com.coachrun.security.EncryptedBigDecimalConverter;
import com.coachrun.security.EncryptedIntegerConverter;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Test physiologique (lactate) d'un athlète : paliers + seuils détectés (cf. DARI Lab).
 * Les valeurs physiologiques sont chiffrées au repos (RGPD art. 9). {@code clubId} dénormalisé
 * pour le scoping tenant.
 */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "lactate_tests")
public class LactateTest extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "club_id", nullable = false)
    private Club club;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "athlete_id", nullable = false)
    private Athlete athlete;

    @Enumerated(EnumType.STRING)
    @Column(name = "test_type", nullable = false, length = 32)
    private TestType testType = TestType.LACTATE;

    @Column(name = "test_date", nullable = false)
    private LocalDate testDate;

    @Column(name = "notes", length = 2048)
    private String notes;

    // --- Valeurs au repos (chiffrées) ---
    @Convert(converter = EncryptedBigDecimalConverter.class)
    @Column(name = "lactate_rest")
    private BigDecimal lactateRest;

    @Convert(converter = EncryptedIntegerConverter.class)
    @Column(name = "hr_rest")
    private Integer hrRest;

    @Convert(converter = EncryptedIntegerConverter.class)
    @Column(name = "hr_max")
    private Integer hrMax;

    // --- Seuils détectés (chiffrés) ---
    @Convert(converter = EncryptedBigDecimalConverter.class)
    @Column(name = "lt1_ms")
    private BigDecimal lt1Ms;

    @Convert(converter = EncryptedBigDecimalConverter.class)
    @Column(name = "lt2_ms")
    private BigDecimal lt2Ms;

    @Convert(converter = EncryptedBigDecimalConverter.class)
    @Column(name = "vc_ms")
    private BigDecimal vcMs;

    @Convert(converter = EncryptedIntegerConverter.class)
    @Column(name = "fc_lt1")
    private Integer fcLt1;

    @Convert(converter = EncryptedIntegerConverter.class)
    @Column(name = "fc_lt2")
    private Integer fcLt2;

    @OneToMany(mappedBy = "test", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("stepOrder ASC")
    private List<LactateTestStep> steps = new ArrayList<>();

    public void replaceSteps(List<LactateTestStep> newSteps) {
        this.steps.clear();
        int i = 0;
        for (LactateTestStep step : newSteps) {
            step.setTest(this);
            step.setStepOrder(i++);
            this.steps.add(step);
        }
    }
}
