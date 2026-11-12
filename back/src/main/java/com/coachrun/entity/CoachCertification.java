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

/**
 * Un diplôme ou une certification, <b>déclaré par le coach</b>.
 *
 * <p>Il n'y a délibérément pas de colonne « vérifié ». La plateforme ne se porte pas garante d'un
 * diplôme qu'elle n'a pas contrôlé auprès de l'organisme émetteur, et un badge ambigu est pire que
 * pas de badge : il transfère à la plateforme une responsabilité qu'elle n'assume pas, et il donne
 * à l'athlète une confiance qu'elle ne fonde pas. La fiche porte une mention unique — « déclarées
 * par le coach » — et l'administrateur regarde les justificatifs au moment de valider la fiche.</p>
 */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "coach_certifications")
public class CoachCertification extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "coach_profile_id", nullable = false)
    private CoachProfile profile;

    /** Intitulé tel que le coach l'écrit (« BPJEPS Athlétisme »). */
    @Column(name = "label", nullable = false, length = 200)
    private String label;

    /** Organisme émetteur (« FFA », « Ministère des Sports »). */
    @Column(name = "organisation", length = 200)
    private String organisation;

    @Column(name = "obtained_year")
    private Integer obtainedYear;
}
