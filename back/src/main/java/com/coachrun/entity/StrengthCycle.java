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
 * Cycle de préparation physique (cf. DARI Lab §7.6) : plan multi-semaines avec progression de
 * charge. La structure (semaines → séances + ajustement de charge) est sérialisée en JSON.
 */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "strength_cycles")
public class StrengthCycle extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "club_id", nullable = false)
    private Club club;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "weeks", nullable = false)
    private int weeks;

    @Column(name = "objective")
    private String objective;

    @Column(name = "description", length = 2048)
    private String description;

    /** JSON : { "weeks": [ { "week": 1, "sessionIds": [...], "chargePctAdjustment": 0 } ] }. */
    @Column(name = "structure_json", length = 20000)
    private String structureJson;
}
