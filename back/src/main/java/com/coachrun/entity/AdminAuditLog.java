package com.coachrun.entity;

import com.coachrun.entity.enums.AdminAuditAction;
import com.coachrun.entity.enums.AdminAuditTarget;
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
 * Trace d'une action d'administration : qui, quoi, sur quelle ressource, quand.
 *
 * <p><b>Pourquoi elle existe.</b> Le back-office pouvait supprimer un compte coach avec tout son
 * historique, changer un rôle, suspendre un club ou ouvrir une session au nom d'un utilisateur —
 * et rien en base n'en gardait la moindre trace. La seule ligne existante était un {@code WARN}
 * applicatif pour l'impersonation, invisible depuis le produit et perdue à la rotation des logs.
 * « Qui a supprimé ce compte ? » était une question sans réponse.</p>
 *
 * <p><b>L'acteur est recopié, pas seulement référencé.</b> {@code actorUserId} peut pointer vers
 * un compte supprimé depuis ; {@code actorEmail} et {@code actorName} figent l'identité au moment
 * du geste. Sans cette recopie, supprimer un administrateur effacerait la lisibilité de tout ce
 * qu'il a fait — exactement l'inverse de ce qu'un journal doit garantir. Pour la même raison, la
 * colonne ne porte <b>aucune clé étrangère</b> : une trace ne bloque jamais une suppression, et
 * ne disparaît pas avec elle.</p>
 *
 * <p><b>Ce qu'elle ne contient jamais.</b> Aucune donnée de santé, aucun mot de passe, aucun
 * jeton, aucune note médicale. Le {@code summary} est composé par le code appelant à partir de
 * champs sûrs (noms, rôles, statuts) — jamais recopié depuis une saisie libre.</p>
 */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "admin_audit_log")
public class AdminAuditLog extends BaseEntity {

    /** Administrateur à l'origine du geste. {@code null} si l'action vient d'une tâche système. */
    @Column(name = "actor_user_id")
    private UUID actorUserId;

    @Column(name = "actor_email", length = 255)
    private String actorEmail;

    @Column(name = "actor_name", length = 255)
    private String actorName;

    /**
     * Lue en {@code STRING} : une valeur retirée du code resterait lisible en base, et
     * {@code AdminAuditAction} ne perd jamais de constante (cf. §4 bis « on ajoute, on ne retire pas »).
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, length = 48)
    private AdminAuditAction action;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 32)
    private AdminAuditTarget targetType = AdminAuditTarget.PLATFORM;

    @Column(name = "target_id")
    private UUID targetId;

    /** Libellé de la cible figé au moment du geste (« Foulées du Lac », « jean@exemple.fr »). */
    @Column(name = "target_label", length = 255)
    private String targetLabel;

    /** Phrase composée par le code : « rôle COACH → HEAD_COACH ». Jamais de saisie libre. */
    @Column(name = "summary", length = 1000)
    private String summary;

    @Column(name = "ip_address", length = 64)
    private String ipAddress;

    @Column(name = "user_agent", length = 255)
    private String userAgent;

    /** Horodatage du geste. Redondant avec {@code createdAt}, mais explicite à la lecture. */
    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt = Instant.now();
}
