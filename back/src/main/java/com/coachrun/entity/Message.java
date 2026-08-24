package com.coachrun.entity;

import com.coachrun.entity.enums.UserRole;
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

import java.util.UUID;

/**
 * Message d'un fil de discussion. {@code workoutId} optionnel pour rattacher un commentaire à une
 * séance précise.
 *
 * <p>{@code athlete} reste porté par le message : c'est lui qui donne son contexte à un échange
 * (la séance commentée, l'écran vers lequel mène la notification). Il ne dit plus <b>qui</b> lit —
 * cela, c'est l'affaire de {@link Conversation}.</p>
 */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "messages")
public class Message extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "club_id", nullable = false)
    private Club club;

    /**
     * L'athlète dont il est question, quand il y en a un : le fil d'un groupe, celui du club et
     * celui de deux coachs n'en désignent aucun.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "athlete_id")
    private Athlete athlete;

    /**
     * Fil porteur. Nullable en base le temps que le backfill rattache les échanges antérieurs au
     * modèle de conversations ; tout message écrit depuis en porte un.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "conversation_id")
    private Conversation conversation;

    @Column(name = "sender_user_id", nullable = false)
    private UUID senderUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "sender_role", nullable = false, length = 16)
    private UserRole senderRole;

    @Column(name = "sender_name", nullable = false)
    private String senderName;

    @Column(name = "body", nullable = false, length = 2000)
    private String body;

    @Column(name = "workout_id")
    private UUID workoutId;

    /**
     * Accusé de lecture côté coach (boîte de réception). Ne concerne que les messages envoyés
     * par l'athlète : un message du coach n'a jamais à être « lu » par lui-même.
     */
    @Column(name = "coach_read_at")
    private java.time.Instant coachReadAt;

    /** Pièce jointe optionnelle : id + métadonnée dénormalisée (octets dans message_attachments). */
    @Column(name = "attachment_id")
    private UUID attachmentId;

    @Column(name = "attachment_filename")
    private String attachmentFilename;

    @Column(name = "attachment_content_type", length = 128)
    private String attachmentContentType;
}
