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
 * Modèle de zones : un jeu nommé de {@link TrainingZone}, appliqué à un ou plusieurs athlètes.
 *
 * <p>Un club n'entraîne pas tout le monde sur la même échelle — route et trail, débutants et
 * confirmés, groupe compétition. Chaque jeu porte donc ses propres zones (nom, couleur, règles) ;
 * l'athlète pointe sur le sien, et {@code null} vaut « jeu par défaut du club ».</p>
 */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "zone_sets")
public class ZoneSet extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "club_id", nullable = false)
    private Club club;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder = 0;

    /** Jeu appliqué aux athlètes qui n'en désignent aucun. Un seul par club. */
    @Column(name = "is_default", nullable = false)
    private boolean defaultSet = false;
}
