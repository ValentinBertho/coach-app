package com.coachrun.entity;

import com.coachrun.entity.enums.RunDrillCategory;
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

/** Éducatif de course (gamme technique / amplitude) d'un club. Distinct de la prépa physique. */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "run_drills")
public class RunDrill extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "club_id", nullable = false)
    private Club club;

    @Column(name = "name", nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 32)
    private RunDrillCategory category;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Column(name = "video_url", length = 512)
    private String videoUrl;
}
