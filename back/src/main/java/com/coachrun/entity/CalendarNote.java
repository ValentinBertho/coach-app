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

import java.time.LocalDate;

/**
 * Note libre du coach posée sur une date du calendrier d'un athlète (chip note, CDC §8).
 * Distincte des notes d'une séance ({@code Workout.notes}).
 */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "calendar_notes")
public class CalendarNote extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "club_id", nullable = false)
    private Club club;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "athlete_id", nullable = false)
    private Athlete athlete;

    @Column(name = "note_date", nullable = false)
    private LocalDate noteDate;

    @Column(name = "text", nullable = false, length = 500)
    private String text;
}
