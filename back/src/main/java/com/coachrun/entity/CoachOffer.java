package com.coachrun.entity;

import com.coachrun.entity.enums.OfferPeriodicity;
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
 * Une formule de coaching : ce que le coach propose, et à quel prix.
 *
 * <p><b>Rien ne s'encaisse sur la plateforme.</b> Ces montants s'affichent, se filtrent, et se
 * recopient sur la demande de coaching au moment de l'accord. Ils existent dès la vitrine pour
 * deux raisons : le tarif est un critère de recherche de l'annuaire, et l'instantané pris à
 * l'acceptation est ce qui protège l'accord passé d'un changement de grille ultérieur.</p>
 *
 * <p>Le montant est en <b>centimes</b>, entier. Un {@code double} sur de la monnaie finit toujours
 * par afficher 89,99999 € à quelqu'un.</p>
 */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "coach_offers")
public class CoachOffer extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "coach_profile_id", nullable = false)
    private CoachProfile profile;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Column(name = "description", length = 1000)
    private String description;

    @Column(name = "amount_cents", nullable = false)
    private int amountCents;

    /** Code ISO 4217. Une seule valeur en pratique aujourd'hui, mais pas de devise implicite. */
    @Column(name = "currency", nullable = false, length = 3)
    private String currency = "EUR";

    @Enumerated(EnumType.STRING)
    @Column(name = "periodicity", nullable = false, length = 16)
    private OfferPeriodicity periodicity = OfferPeriodicity.MONTHLY;

    /**
     * Formule proposée aujourd'hui. Une formule retirée n'est pas supprimée : elle est peut-être
     * celle sur laquelle un athlète a été accepté, et son libellé doit rester lisible.
     */
    @Column(name = "active", nullable = false)
    private boolean active = true;

    /** Ordre d'affichage sur la fiche, décidé par le coach. */
    @Column(name = "position", nullable = false)
    private int position = 0;
}
