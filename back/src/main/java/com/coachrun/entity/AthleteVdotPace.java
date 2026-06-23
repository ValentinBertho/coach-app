package com.coachrun.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Allures d'équivalence de course (secondes/km par distance) calculées à partir du VDOT
 * (cf. DARI Lab). Une ligne par athlète, recalculée automatiquement à chaque performance.
 */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "athlete_vdot_paces",
        uniqueConstraints = @UniqueConstraint(name = "uq_vdot_paces_athlete", columnNames = "athlete_id"))
public class AthleteVdotPace extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "athlete_id", nullable = false)
    private Athlete athlete;

    @Column(name = "vdot", precision = 5, scale = 2)
    private BigDecimal vdot;

    @Column(name = "pace_800m_s")
    private Integer pace800mS;

    @Column(name = "pace_1500m_s")
    private Integer pace1500mS;

    @Column(name = "pace_3000m_s")
    private Integer pace3000mS;

    @Column(name = "pace_5km_s")
    private Integer pace5kmS;

    @Column(name = "pace_10km_s")
    private Integer pace10kmS;

    @Column(name = "pace_15km_s")
    private Integer pace15kmS;

    @Column(name = "pace_semi_s")
    private Integer paceSemiS;

    @Column(name = "pace_marathon_s")
    private Integer paceMarathonS;
}
