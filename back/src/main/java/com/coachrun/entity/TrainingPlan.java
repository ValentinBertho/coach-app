package com.coachrun.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.HashSet;
import java.util.Set;

/**
 * Plan d'entraînement périodisé (N semaines). Les items (semaine × jour → modèle de séance)
 * sont stockés sérialisés en JSON. Appliqué à un athlète avec une date de départ.
 */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "training_plans")
public class TrainingPlan extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "club_id", nullable = false)
    private Club club;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description", length = 2048)
    private String description;

    @Column(name = "duration_weeks", nullable = false)
    private int durationWeeks;

    /** JSON sérialisé de la liste d'items (PlanItemDto[]). */
    @Column(name = "items_json", length = 8000)
    private String itemsJson;

    /**
     * Athlètes auxquels ce plan est attribué.
     * Ouverture many-to-many : un plan peut être attribué à plusieurs athlètes,
     * et un athlète peut suivre plusieurs plans.
     */
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "training_plan_athletes",
            joinColumns = @JoinColumn(name = "plan_id"),
            inverseJoinColumns = @JoinColumn(name = "athlete_id"))
    private Set<Athlete> athletes = new HashSet<>();
}
