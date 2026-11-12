package com.coachrun.entity;

import com.coachrun.entity.enums.CoachReportReason;
import com.coachrun.entity.enums.CoachReportStatus;
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
 * Le signalement d'une fiche coach — la contrepartie de la décision 4.
 *
 * <p>La plateforme affiche les diplômes comme <b>déclarés</b>, sans les vérifier. Elle a donc
 * renoncé à garantir, et il lui reste l'obligation d'écouter : un annuaire qui publie des
 * affirmations non vérifiées sans offrir de les contester donne l'autorité de la publication sans
 * le recours qui la rend supportable.</p>
 *
 * <h2>Pourquoi le signalant peut être anonyme</h2>
 *
 * <p>{@link #reporter} est nullable, et ce n'est pas un oubli. Exiger un compte écarterait
 * précisément ceux qui ont le plus de raisons de signaler : un confrère qui reconnaît un diplôme
 * qu'il sait faux, quelqu'un qui a mal vécu une relation et ne souhaite pas se rattacher à la
 * plateforme pour le dire. La contrepartie est ailleurs — un seau de limitation dédié et l'adresse
 * IP conservée, comme sur les autres dépôts anonymes du produit.</p>
 */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "coach_profile_reports")
public class CoachProfileReport extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "profile_id", nullable = false)
    private CoachProfile profile;

    /** Nullable : le signalement anonyme est accepté, cf. l'en-tête de classe. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reporter_user_id")
    private User reporter;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason", nullable = false, length = 32)
    private CoachReportReason reason;

    /** Le texte du signalant. La catégorie oriente, c'est celui-ci qui explique. */
    @Column(name = "details", length = 2000)
    private String details;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private CoachReportStatus status = CoachReportStatus.OPEN;

    @Column(name = "handled_at")
    private Instant handledAt;

    @Column(name = "handled_by_user_id")
    private UUID handledByUserId;

    /**
     * Ce que l'administrateur a constaté.
     *
     * <p>Jamais renvoyé au signalant : il peut contenir des éléments sur le coach — un diplôme
     * effectivement produit, une explication donnée par téléphone — qui n'appartiennent pas à
     * celui qui a signalé.</p>
     */
    @Column(name = "moderator_note", length = 2000)
    private String moderatorNote;

    @Column(name = "ip_address", length = 64)
    private String ipAddress;

    @Column(name = "user_agent", length = 512)
    private String userAgent;

    /**
     * Clôt le signalement. Idempotent : deux administrateurs qui cliquent en même temps ne doivent
     * pas réécrire l'horodatage du premier, ni le nom qui figure au dossier.
     */
    public void handle(CoachReportStatus outcome, UUID byUserId, String note) {
        if (!status.isOpen()) {
            return;
        }
        this.status = outcome;
        this.handledAt = Instant.now();
        this.handledByUserId = byUserId;
        this.moderatorNote = note;
    }
}
