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

    /**
     * Vrai quand cet espace est celui d'un <b>coach indépendant</b>, et non d'un club.
     *
     * <p>Ne porte aucun droit : le club reste le tenant, avec ses membres, ses zones et sa
     * bibliothèque. Il décide seulement du <b>vocabulaire</b>. La plateforme s'adresse aux coachs
     * indépendants autant qu'aux clubs, mais leur parlait à tous de « club » — nom de club
     * obligatoire à l'inscription, entrée « Club » dans la navigation, périmètre « Tout le club »,
     * bilan « Ta semaine de club ». Pour un indépendant, ces mots décrivent une organisation qui
     * n'existe pas, et c'est la première chose qu'il lit du produit.</p>
     */
    @Column(name = "solo_practice", nullable = false)
    private boolean soloPractice = false;

    /**
     * Bornes des domaines d'intensité par défaut, appliquées aux NOUVEAUX athlètes du club.
     * Les athlètes existants gardent les leurs : un réglage de défaut ne réécrit jamais
     * rétroactivement des valeurs déjà personnalisées.
     */
    @Column(name = "default_vc_domain1_pct", precision = 5, scale = 2)
    private java.math.BigDecimal defaultVcDomain1Pct = new java.math.BigDecimal("90.00");

    @Column(name = "default_vc_domain2_pct", precision = 5, scale = 2)
    private java.math.BigDecimal defaultVcDomain2Pct = new java.math.BigDecimal("100.00");

    @Column(name = "default_fc_domain1_pct", precision = 5, scale = 2)
    private java.math.BigDecimal defaultFcDomain1Pct = new java.math.BigDecimal("80.00");

    @Column(name = "default_fc_domain2_pct", precision = 5, scale = 2)
    private java.math.BigDecimal defaultFcDomain2Pct = new java.math.BigDecimal("90.00");
}
