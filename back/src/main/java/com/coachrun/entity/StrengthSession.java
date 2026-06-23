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

import java.time.Instant;

/**
 * Séance de préparation physique (bibliothèque, cf. DARI Lab). La structure (blocs + exercices
 * prescrits) est sérialisée en JSON, à l'image de la bibliothèque course. Club-scopée.
 */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "strength_sessions")
public class StrengthSession extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "club_id", nullable = false)
    private Club club;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "notes", length = 2048)
    private String notes;

    @Column(name = "is_favorite", nullable = false)
    private boolean favorite = false;

    @Column(name = "is_archived", nullable = false)
    private boolean archived = false;

    @Column(name = "use_count", nullable = false)
    private int useCount = 0;

    @Column(name = "last_used_at")
    private Instant lastUsedAt;

    /** JSON sérialisé de la structure DARI Lab (blocs + exercices prescrits). */
    @Column(name = "structure_json", length = 20000)
    private String structureJson;
}
