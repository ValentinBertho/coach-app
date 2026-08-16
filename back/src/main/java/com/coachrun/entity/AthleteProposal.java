package com.coachrun.entity;

import com.coachrun.entity.enums.ProposalStatus;
import com.coachrun.entity.enums.ProposalType;
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

import java.time.Instant;
import java.util.UUID;

/**
 * Une proposition en attente de décision humaine.
 *
 * <h2>Pourquoi cette table existe</h2>
 *
 * <p>Le produit sait beaucoup de choses — qu'une fatigue à 8 rend le fractionné du jour
 * déraisonnable, qu'une sortie de dimanche vaut un VDOT supérieur au profil, qu'un exercice réussi
 * à RIR 3 appelle 2,5 kg de plus. La tentation serait d'agir. <b>Le produit n'agit jamais.</b>
 * Il écrit ici ce qu'il ferait, avec sa raison, et attend qu'un humain tape dessus.</p>
 *
 * <p>Cette règle n'est pas une prudence technique, c'est le métier : un plan d'entraînement est
 * l'engagement d'un coach envers son athlète. Une application qui le réécrit toute seule pendant la
 * nuit détruit exactement ce qu'elle prétend servir — et le coach, découvrant une séance qu'il n'a
 * pas écrite, cesse de faire confiance à toutes les autres.</p>
 *
 * <h2>Ce que la table garantit</h2>
 *
 * <p>Une seule porte d'écriture ({@code ProposalService#accept}) pour toutes les suggestions, quelle
 * que soit leur origine. Impossible d'ajouter une automatisation silencieuse sans passer par ici, et
 * donc sans qu'elle soit visible, refusable, et tracée ({@code decidedBy}, {@code decidedAt}).</p>
 *
 * <p>La charge utile est en JSON parce que les types n'ont rien en commun : une date de report, un
 * VDOT, un pourcentage de charge. La colonner reviendrait à créer six tables presque vides.</p>
 */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "athlete_proposals")
public class AthleteProposal extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "club_id", nullable = false)
    private Club club;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "athlete_id", nullable = false)
    private Athlete athlete;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 32)
    private ProposalType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private ProposalStatus status = ProposalStatus.PENDING;

    /**
     * Objet visé quand il y en a un : la séance à alléger, à décaler, à supprimer. Nul pour une
     * proposition qui porte sur l'athlète lui-même (valeur physiologique).
     */
    @Column(name = "target_id")
    private UUID targetId;

    /**
     * Ce que la proposition ferait, en une phrase écrite pour être lue telle quelle dans l'interface.
     * Générée au moment de la détection : la reformuler à l'affichage, c'est risquer que deux écrans
     * ne disent pas la même chose de la même proposition.
     */
    @Column(name = "title", nullable = false, length = 200)
    private String title;

    /**
     * Pourquoi. Le chiffre qui a déclenché la proposition, en clair — « fatigue 8/10, charge 40 %
     * au-dessus de l'habituel ». Sans cette ligne, accepter revient à faire confiance à une boîte
     * noire, ce qu'aucun coach ne fait deux fois.
     */
    @Column(name = "reason", length = 500)
    private String reason;

    /** Paramètres d'application, propres au type (date cible, valeur, pourcentage). */
    @Column(name = "payload_json", columnDefinition = "TEXT")
    private String payloadJson;

    /**
     * Vrai si c'est l'athlète qui a demandé (feu vert du matin), faux si le produit l'a détecté.
     * La distinction change l'urgence : une demande d'athlète attend une réponse, une détection non.
     */
    @Column(name = "requested_by_athlete", nullable = false)
    private boolean requestedByAthlete = false;

    /** Jour concerné, s'il y en a un — sert à périmer les propositions que la date a dépassées. */
    @Column(name = "target_date")
    private java.time.LocalDate targetDate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "decided_by")
    private User decidedBy;

    @Column(name = "decided_at")
    private Instant decidedAt;

    /**
     * Empreinte de ce qui a déclenché la proposition, pour ne pas reproposer la même chose chaque
     * nuit. Même rôle que {@code AlertDigestLog} pour les alertes : sans elle, un athlète qui
     * ignore une suggestion la retrouve tous les matins et finit par ignorer toutes les autres.
     */
    @Column(name = "dedupe_key", nullable = false, length = 200)
    private String dedupeKey;
}
