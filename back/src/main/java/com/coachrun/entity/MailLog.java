package com.coachrun.entity;

import com.coachrun.entity.enums.MailKind;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Trace d'un e-mail sortant : ce qui a été envoyé, à qui, quand, et si le fournisseur l'a accepté.
 *
 * <p><b>Pourquoi elle existe.</b> Le produit s'appuie sur un plafond d'envoi (quelques milliers par
 * mois, cent par jour) que rien ne mesurait : la seule façon de savoir combien on consommait était
 * d'ouvrir la console du fournisseur, et la seule façon d'apprendre qu'on l'avait dépassé était
 * qu'un coach signale n'avoir jamais reçu son lien de réinitialisation. Or c'est exactement
 * l'envoi qu'on ne peut pas perdre.</p>
 *
 * <p><b>Ce qu'elle ne contient pas.</b> Ni le corps de l'e-mail, ni aucune donnée de santé : le
 * sujet, le destinataire, la nature de l'envoi et son issue suffisent à répondre aux deux seules
 * questions qu'on se pose — « combien ai-je consommé » et « untel a-t-il bien reçu son lien ».</p>
 *
 * <p><b>Durée de conservation.</b> L'adresse est une donnée personnelle : la table est purgée
 * au-delà de {@code app.mail.log-retention-days} (180 jours par défaut), ce qui couvre largement
 * un diagnostic et une facturation, sans constituer un historique perpétuel.</p>
 */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "mail_log")
public class MailLog extends BaseEntity {

    /** Destinataire. Sert au diagnostic individuel (« untel a-t-il reçu son invitation ? »). */
    @Column(nullable = false, length = 255)
    private String recipient;

    @Column(nullable = false, length = 255)
    private String subject;

    /** Nature de l'envoi : l'axe d'analyse de la consommation. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private MailKind kind = MailKind.OTHER;

    /** COACH ou ATHLETE : le gabarit et le lien de désabonnement en dépendent. */
    @Column(length = 16)
    private String audience;

    /** L'envoi a-t-il été accepté par le fournisseur ? */
    @Column(nullable = false)
    private boolean sent = true;

    /**
     * Message d'échec, tronqué. C'est lui qui distingue un plafond atteint d'une adresse invalide
     * — deux pannes qui se ressemblent depuis l'application et n'appellent pas la même réaction.
     */
    @Column(name = "error_message", length = 500)
    private String errorMessage;

    /** Horodatage de la tentative. Redondant avec {@code createdAt}, mais explicite à la lecture. */
    @Column(name = "sent_at", nullable = false)
    private Instant sentAt = Instant.now();
}
