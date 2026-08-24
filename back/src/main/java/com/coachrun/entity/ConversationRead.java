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
import java.util.UUID;

/**
 * Où en est <b>cette personne</b> dans <b>ce fil</b>.
 *
 * <p>« Lu » était un attribut du message ({@code coach_read_at}), ce qui ne voulait déjà plus rien
 * dire à plusieurs coachs — et rien du tout dans un fil de groupe.</p>
 */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "conversation_reads")
public class ConversationRead extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "conversation_id", nullable = false)
    private Conversation conversation;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "last_read_at", nullable = false)
    private Instant lastReadAt;
}
