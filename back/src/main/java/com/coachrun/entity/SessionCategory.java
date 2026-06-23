package com.coachrun.entity;

import com.coachrun.entity.enums.Discipline;
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

/**
 * Catégorie hiérarchique de la bibliothèque de séances course (cf. DARI Lab — arbre éditable
 * « s-library »). Rattachée à un club ; un parent optionnel permet une arborescence libre.
 */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "session_categories")
public class SessionCategory extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "club_id", nullable = false)
    private Club club;

    @Column(name = "name", nullable = false)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private SessionCategory parent;

    /** Discipline associée (route/trail) ou {@code null} = toutes. */
    @Enumerated(EnumType.STRING)
    @Column(name = "discipline", length = 16)
    private Discipline discipline;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder = 0;
}
