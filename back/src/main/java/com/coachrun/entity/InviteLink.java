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

import java.time.Instant;

/**
 * Un lien d'invitation : ce qu'un coach envoie par SMS, WhatsApp ou e-mail pour qu'un athlète le
 * rejoigne en un geste.
 *
 * <p>Deux usages, une seule forme. Le lien <b>personnel</b>, envoyé à une personne précise, donne
 * une entrée directe ({@code directEntry}) : la personne est suivie dès qu'elle l'a ouvert. Le lien
 * <b>public</b>, collé sur un groupe WhatsApp ou une affiche de club, dépose une demande que le
 * coach valide — il peut tomber entre toutes les mains.</p>
 */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "invite_links")
public class InviteLink extends BaseEntity {

    @Column(name = "token", nullable = false, unique = true, length = 32)
    private String token;

    /** Le coach qui invite, et qui sera référent. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "coach_id", nullable = false)
    private User coach;

    /** L'espace où la fiche naîtra : celui du coach. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "club_id", nullable = false)
    private Club club;

    /** Groupe d'entraînement d'arrivée, facultatif. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id")
    private TrainingGroup group;

    /** Libellé pour le coach (« Groupe du mardi », « Pour Léa »). */
    @Column(name = "label", length = 120)
    private String label;

    /** L'athlète rejoint le club (athlète du club), pas seulement le coach (athlète privé). */
    @Column(name = "club_membership", nullable = false)
    private boolean clubMembership;

    /** Entrée directe, sans validation du coach. */
    @Column(name = "direct_entry", nullable = false)
    private boolean directEntry;

    @Column(name = "max_uses")
    private Integer maxUses;

    @Column(name = "uses", nullable = false)
    private int uses;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    /** Pourquoi le lien ne sert plus, ou {@code null} s'il est utilisable. */
    public String unusableReason(Instant now) {
        if (revokedAt != null) {
            return "Ce lien a été désactivé par le coach.";
        }
        if (expiresAt != null && !expiresAt.isAfter(now)) {
            return "Ce lien a expiré.";
        }
        if (maxUses != null && uses >= maxUses) {
            return "Ce lien a déjà servi le nombre de fois prévu.";
        }
        return null;
    }
}
