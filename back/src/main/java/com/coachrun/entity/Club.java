package com.coachrun.entity;

import com.coachrun.entity.enums.ClubStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Club = tenant principal de la plateforme (ou club implicite pour un coach solo).
 * Entité minimale créée pour valider la chaîne Liquibase / JPA (cf. changeset 001).
 */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "clubs")
public class Club extends BaseEntity {

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "slug", nullable = false, unique = true)
    private String slug;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private ClubStatus status = ClubStatus.ACTIVE;
}
