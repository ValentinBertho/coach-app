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
     * Rôle de l'acteur <b>au moment du geste</b>, figé comme son e-mail et pour la même raison.
     *
     * <p>Un rôle change : un head coach devient coach, un administrateur est rétrogradé. Sans
     * cette recopie, relire le journal six mois plus tard ne dit plus de quel droit l'action a
     * été faite — et c'est exactement la question qu'on pose à un journal d'audit quand elle
     * commence à se poser.</p>
     */
    @Column(name = "actor_role", length = 32)
    private String actorRole;

    /**
     * Administrateur réellement aux commandes, quand le geste a été fait depuis une session
     * <b>empruntée</b> ({@code /admin/users/{id}/impersonate}).
     *
     * <p><b>Le trou que ces deux colonnes bouchent.</b> L'impersonation existe depuis longtemps et
     * son ouverture était bien consignée — mais rien ensuite. Une action faite par un
     * administrateur au nom d'un coach était enregistrée comme celle du coach, sans la moindre
     * marque : {@code ImpersonationService} le disait en toutes lettres (« écrire depuis un compte
     * emprunté laisse une trace indiscernable de celle de son titulaire »). Un journal qui
     * attribue un geste à quelqu'un qui ne l'a pas fait est pire qu'un journal muet : il accuse.</p>
     *
     * <p>Le jeton portait pourtant déjà le claim {@code imp}. Il n'était lu nulle part ; il l'est
     * maintenant, et il arrive jusqu'ici.</p>
     *
     * <p>Nul dans l'immense majorité des lignes — c'est le cas normal.</p>
     */
    @Column(name = "impersonator_user_id")
    private UUID impersonatorUserId;

    /** Identité de l'administrateur derrière l'emprunt, figée comme celle de l'acteur. */
    @Column(name = "impersonator_email", length = 255)
    private String impersonatorEmail;

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

    /**
     * Méthode et chemin HTTP de l'appel. De quoi recouper une ligne du journal avec les journaux
     * applicatifs, et savoir quel endpoint a servi — deux gestes voisins du back-office peuvent
     * produire la même action sur la même cible par des routes différentes.
     *
     * <p>Le chemin est celui du gabarit servi, jamais une chaîne de requête : celle-ci peut
     * transporter des filtres saisis à la main, donc de la donnée qui n'a rien à faire ici.</p>
     */
    @Column(name = "request_method", length = 8)
    private String requestMethod;

    @Column(name = "request_path", length = 255)
    private String requestPath;

    /** Horodatage du geste. Redondant avec {@code createdAt}, mais explicite à la lecture. */
    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt = Instant.now();
}
