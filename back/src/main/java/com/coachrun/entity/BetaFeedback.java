package com.coachrun.entity;

import com.coachrun.entity.enums.FeedbackKind;
import com.coachrun.entity.enums.FeedbackStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * Retour envoyé depuis l'application pendant la bêta : bug, idée ou question.
 *
 * <p>Le contexte technique est capturé au moment de l'envoi plutôt que redemandé ensuite. Le
 * {@code correlationId} est celui que {@code GlobalExceptionHandler} produit déjà à chaque
 * erreur serveur et affiche à l'utilisateur : le recueillir ici est ce qui permet de relier
 * « ça a planté quand j'ai enregistré » à une trace précise, sans deviner.</p>
 *
 * <p>L'auteur est une simple référence nullable : un retour survit à la suppression du compte de
 * celui qui l'a écrit (contrainte {@code ON DELETE SET NULL}). Le corps du message est libre —
 * on ne peut donc pas exclure qu'un utilisateur y écrive une information personnelle — mais rien
 * n'y est collecté automatiquement au-delà de la page, de la version et du navigateur.</p>
 */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "beta_feedback")
public class BetaFeedback extends BaseEntity {

    /** Auteur, ou {@code null} si son compte a été supprimé depuis. */
    @Column(name = "user_id")
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 16)
    private FeedbackKind kind = FeedbackKind.BUG;

    @Column(name = "message", nullable = false, length = 4000)
    private String message;

    /** Écran depuis lequel le retour a été envoyé (chemin front, sans domaine). */
    @Column(name = "page", length = 512)
    private String page;

    /** Version de l'application au moment du retour — sans elle, « ça marchait hier » est indécidable. */
    @Column(name = "app_version", length = 64)
    private String appVersion;

    @Column(name = "user_agent", length = 512)
    private String userAgent;

    /** Identifiant de corrélation de la dernière erreur serveur vue par l'utilisateur. */
    @Column(name = "correlation_id", length = 64)
    private String correlationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private FeedbackStatus status = FeedbackStatus.NEW;
}
