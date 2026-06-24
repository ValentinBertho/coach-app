package com.coachrun.entity;

import com.coachrun.entity.enums.UnavailabilityReason;
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

import java.time.LocalDate;

/**
 * Période d'indisponibilité d'un athlète (blessure, maladie, vacances…), cf. DARI Lab.
 * Affichée sur le calendrier (bandeau) et prise en compte par le coach lors de la planification.
 */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "athlete_unavailabilities")
public class AthleteUnavailability extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "club_id", nullable = false)
    private Club club;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "athlete_id", nullable = false)
    private Athlete athlete;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason", nullable = false, length = 16)
    private UnavailabilityReason reason = UnavailabilityReason.OTHER;

    @Column(name = "notes", length = 512)
    private String notes;
}
