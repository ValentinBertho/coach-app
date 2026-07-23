package com.coachrun.entity;

import com.coachrun.entity.enums.Discipline;
import com.coachrun.entity.enums.ZoneScope;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
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

import java.util.ArrayList;
import java.util.List;

/**
 * Zone de travail nommée par le coach (Récupération, Endurance, Seuil, VO2…), en nombre libre et
 * ré-ordonnable. Cf. AUDIT-COACH-ZONES-METRIQUES §3.1 — remplace, à terme, les zones figées Z1–Z5.
 *
 * <p>La zone porte un jeu de métriques ({@link ZoneMetric}) ; les valeurs concrètes par athlète
 * vivent sur la fiche athlète (chantier Z2).</p>
 */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "training_zones")
public class TrainingZone extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "club_id", nullable = false)
    private Club club;

    @Column(name = "name", nullable = false)
    private String name;

    /** Couleur d'affichage (pastille), au format CSS (ex. {@code #22c55e}). */
    @Column(name = "color", length = 16)
    private String color;

    /** Description libre de la zone (objectif, ressenti attendu…). */
    @Column(name = "description", length = 1024)
    private String description;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder = 0;

    @Enumerated(EnumType.STRING)
    @Column(name = "scope", nullable = false, length = 16)
    private ZoneScope scope = ZoneScope.CLUB;

    /** Discipline associée (route/trail) ou {@code null} = toutes. */
    @Enumerated(EnumType.STRING)
    @Column(name = "discipline", length = 16)
    private Discipline discipline;

    @Column(name = "is_builtin", nullable = false)
    private boolean builtin = false;

    /** Métriques portées par la zone (config pilotant l'écran fiche athlète et l'affichage). */
    @OneToMany(mappedBy = "zone", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("sortOrder ASC")
    private List<ZoneMetric> metrics = new ArrayList<>();
}
