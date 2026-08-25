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
 * Note libre du coach posée sur le calendrier d'un athlète (chip note, CDC §8).
 * Distincte des notes d'une séance ({@code Workout.notes}).
 *
 * <p>Une note couvre <b>un jour ou une période</b>. La seconde forme est ce qu'un coach appelle un
 * cycle — « bloc spécifique », « affûtage » — et elle n'avait aucune écriture : il fallait répéter
 * la même note chaque lundi, ou renoncer à l'inscrire. {@code endDate} nul = note d'un jour, ce que
 * reste toute note saisie avant.</p>
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

    /** Dernier jour couvert (inclus), ou {@code null} pour une note d'un seul jour. */
    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "text", nullable = false, length = 500)
    private String text;

    /**
     * Lisible par l'autre partie ?
     *
     * <p>Faux par défaut, et faux pour toute note antérieure : les notes d'un jour sont le carnet
     * de travail du coach — « relancer sur le sommeil », « surveiller ce genou » — écrites en le
     * croyant privé. Une note qu'un athlète écrit, elle, n'a de sens que partagée : c'est un mot
     * qu'il adresse.</p>
     */
    @Column(name = "shared", nullable = false)
    private boolean shared;

    /** Auteur de la note. Nul pour les notes antérieures, qui sont du coach par construction. */
    @Column(name = "author_user_id")
    private java.util.UUID authorUserId;

    @jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
    @Column(name = "author_role", length = 16)
    private com.coachrun.entity.enums.UserRole authorRole;

    /** Un cycle couvre une période ; une note d'un jour n'en couvre qu'un. */
    public boolean isCycle() {
        return endDate != null && endDate.isAfter(noteDate);
    }

    /**
     * Cette note est-elle visible de l'athlète ?
     *
     * <p>Les cycles l'ont toujours été — ils décrivent sa préparation. Le reste ne l'est que si
     * le coach l'a explicitement adressé, ou si c'est l'athlète lui-même qui l'a écrit.</p>
     */
    public boolean isVisibleToAthlete() {
        return shared || isCycle()
                || authorRole == com.coachrun.entity.enums.UserRole.ATHLETE;
    }
}
