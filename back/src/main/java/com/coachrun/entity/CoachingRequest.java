package com.coachrun.entity;

import com.coachrun.entity.enums.CoachingRequestStatus;
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
 * Une demande de coaching : le geste qui relie un athlète à un coach.
 *
 * <h2>Pourquoi ce n'est pas une {@code AthleteProposal}</h2>
 *
 * <p>Le nom invite à la confusion, et il faut la lever : {@code AthleteProposal} ne porte que des
 * <b>ajustements d'entraînement</b> — alléger une séance, décaler, mettre à jour une valeur physio —
 * chacun adossé à une branche de son dispatcher. Y ajouter la mise en relation aurait mélangé, dans
 * la même file, « alléger la séance de mardi » et « Marie voudrait que vous la coachiez ». Le patron
 * repris ici est celui des demandes de création de club : une file, une décision, un motif.</p>
 *
 * <h2>L'échange, et ses limites</h2>
 *
 * <p>Il n'y a pas de messagerie avant l'acceptation. La demande porte le mot de l'athlète, et le
 * coach peut poser <b>une</b> question, à laquelle l'athlète répond <b>une</b> fois. Trois raisons,
 * dans l'ordre : cela évite d'ouvrir un canal de spam vers tous les coachs publiés le jour du
 * lancement ; cela évite la modération d'une messagerie entre inconnus, qui est un métier ; et cela
 * laisse {@code ConversationService} intact, lui dont toutes les règles de participation se déduisent
 * d'une appartenance qui n'existe pas encore ici.</p>
 */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "coaching_requests")
public class CoachingRequest extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "athlete_account_id", nullable = false)
    private AthleteAccount athleteAccount;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "coach_user_id", nullable = false)
    private User coach;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private CoachingRequestStatus status = CoachingRequestStatus.PENDING;

    /** Le mot de l'athlète : son objectif, son contexte, ce qu'il attend. */
    @Column(name = "message", length = 2000)
    private String message;

    /** L'unique question du coach, s'il en pose une avant de décider. */
    @Column(name = "coach_question", length = 1000)
    private String coachQuestion;

    /** L'unique réponse de l'athlète à cette question. */
    @Column(name = "athlete_answer", length = 1000)
    private String athleteAnswer;

    /** Formule souhaitée. Nullable : l'athlète peut demander sans se prononcer sur le tarif. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "offer_id")
    private CoachOffer offer;

    /**
     * Le libellé et le montant de la formule, <b>recopiés</b> au moment de la demande.
     *
     * <p>Une clé étrangère seule ne suffit pas : le coach peut changer sa grille six mois plus
     * tard, et l'accord passé doit rester lisible tel qu'il a été conclu. C'est la même raison qui
     * fait qu'on ne supprime pas une formule retirée — deux protections pour une donnée qui est le
     * prix convenu entre deux personnes.</p>
     */
    @Column(name = "offer_label", length = 120)
    private String offerLabel;

    @Column(name = "offer_amount_cents")
    private Integer offerAmountCents;

    @Column(name = "decided_at")
    private Instant decidedAt;

    /** Motif d'un refus. Transmis à l'athlète, jamais publié. */
    @Column(name = "decline_reason", length = 1000)
    private String declineReason;

    /**
     * Au-delà, la demande est périmée — pas refusée.
     *
     * <p>Aligné sur la validité d'une invitation athlète : quatorze jours. Un coach qui n'a pas
     * répondu en deux semaines ne répondra pas, et laisser la demande ouverte indéfiniment ferait
     * attendre l'athlète pour rien.</p>
     */
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    /** Ce que l'acceptation a créé — comme {@code ClubCreationRequest.created_club_id}. */
    @Column(name = "created_athlete_id")
    private UUID createdAthleteId;

    @Column(name = "created_relation_id")
    private UUID createdRelationId;

    /** Traces anti-abus, même patron que le dépôt d'une demande de création de club. */
    @Column(name = "ip_address", length = 64)
    private String ipAddress;

    @Column(name = "user_agent", length = 512)
    private String userAgent;

    /** Vrai si la demande attend encore une décision à l'instant donné. */
    public boolean isOpenAt(Instant now) {
        return status.isOpen() && expiresAt.isAfter(now);
    }
}
