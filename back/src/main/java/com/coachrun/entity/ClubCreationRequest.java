package com.coachrun.entity;

import com.coachrun.entity.enums.ClubRequestStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Demande de création de club, déposée depuis la page d'inscription et arbitrée depuis le
 * back-office plateforme.
 *
 * <h2>Ce qu'elle remplace</h2>
 *
 * <p>Deux régimes existaient, et aucun ne convient à une bêta ouverte. En mode {@code open},
 * {@code /auth/register} créait un club et un compte propriétaire sur la seule unicité de
 * l'adresse : n'importe qui, y compris un robot, repartait avec un espace complet. En mode
 * {@code invite}, un code partagé fermait la porte — mais un code se transfère, se colle dans un
 * message, et ne dit jamais qui s'en est servi ; il fallait le distribuer à la main à chaque
 * nouveau coach, et le changer dès qu'il fuitait.</p>
 *
 * <p>Ici, le formulaire reste ouvert à tous : c'est une demande, pas un compte. Rien n'est créé
 * tant qu'un administrateur n'a pas tranché — et la validation crée le club <b>et</b> le compte
 * du coach en un geste.</p>
 *
 * <h2>Ce que la ligne conserve, et pourquoi</h2>
 *
 * <p>Une demande refusée n'est pas supprimée : un refus se relit (« pourquoi ce club n'a-t-il
 * jamais été ouvert ? ») et se conteste. Une demande validée garde l'identifiant du club et du
 * compte créés, ce qui relie l'espace à la décision qui l'a autorisé — c'est la seule trace qui
 * répond à « qui a laissé entrer ce club ».</p>
 *
 * <p>Le message est du texte libre : le candidat peut y écrire ce qu'il veut. Il n'est jamais
 * journalisé, il se lit dans le back-office.</p>
 */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "club_creation_request")
public class ClubCreationRequest extends BaseEntity {

    /** Nom du club souhaité. Repris tel quel à la création. */
    @Column(name = "club_name", nullable = false, length = 120)
    private String clubName;

    /** Nom du demandeur, qui deviendra le coach propriétaire du club. */
    @Column(name = "full_name", nullable = false, length = 120)
    private String fullName;

    /** Adresse du demandeur, en minuscules : c'est la clé de l'unicité des comptes. */
    @Column(name = "email", nullable = false, length = 255)
    private String email;

    /** Téléphone, facultatif : de quoi rappeler un candidat plutôt que de refuser dans le vide. */
    @Column(name = "phone", length = 40)
    private String phone;

    /** « Parlez-nous de votre structure » — texte libre, lu à l'arbitrage. */
    @Column(name = "message", length = 2000)
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private ClubRequestStatus status = ClubRequestStatus.PENDING;

    /**
     * Le candidat s'est déclaré <b>indépendant</b> : la validation ouvrira un espace solo, dont on
     * ne lui parlera jamais comme d'un club. Porté par la demande et non déduit à la validation :
     * c'est le candidat qui sait comment il exerce, pas l'administrateur qui arbitre.
     */
    @Column(name = "solo_practice", nullable = false)
    private boolean soloPractice = false;

    /** Preuve de consentement RGPD, posée au dépôt (la case est obligatoire au formulaire). */
    @Column(name = "terms_accepted_at")
    private Instant termsAcceptedAt;

    /** Adresse d'appel du dépôt : de quoi reconnaître une salve venue d'un même point. */
    @Column(name = "ip_address", length = 64)
    private String ipAddress;

    @Column(name = "user_agent", length = 512)
    private String userAgent;

    /** Quand la demande a été arbitrée ; {@code null} tant qu'elle attend. */
    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    /**
     * Administrateur qui a tranché. Référence nue, sans clé étrangère : la décision doit survivre
     * à la suppression du compte qui l'a prise — et ne jamais l'empêcher.
     */
    @Column(name = "reviewed_by_user_id")
    private UUID reviewedByUserId;

    /** Adresse de l'arbitre, recopiée : la trace reste lisible après suppression de son compte. */
    @Column(name = "reviewed_by_email", length = 255)
    private String reviewedByEmail;

    /** Motif du refus, ou note posée à la validation. Envoyé au demandeur en cas de refus. */
    @Column(name = "review_note", length = 1000)
    private String reviewNote;

    /** Club créé à la validation. {@code null} pour une demande en attente ou refusée. */
    @Column(name = "created_club_id")
    private UUID createdClubId;

    /** Compte coach créé à la validation. */
    @Column(name = "created_user_id")
    private UUID createdUserId;

    /** Vrai tant que la demande n'a pas été arbitrée. */
    public boolean isPending() {
        return status == ClubRequestStatus.PENDING;
    }
}
