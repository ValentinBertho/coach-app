package com.coachrun.entity;

import com.coachrun.entity.enums.ConversationKind;
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
 * Un fil de discussion.
 *
 * <p>La messagerie n'avait pas de fil : elle avait un athlète, et tout coach ayant accès à lui
 * lisait le tas. Le fil devient l'unité — et son identité tient dans {@link #dedupKey}, sous
 * contrainte d'unicité : deux clients qui ouvrent la même conversation au même instant n'en créent
 * qu'une. Un index unique multi-colonnes ne suffirait pas, ses NULL étant toujours distincts et
 * trois formes de fil sur quatre en portant.</p>
 */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "conversations")
public class Conversation extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "club_id", nullable = false)
    private Club club;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 16)
    private ConversationKind kind;

    /** ATHLETE_COACH : l'athlète du binôme. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "athlete_id")
    private Athlete athlete;

    /** ATHLETE_COACH : le coach du binôme. */
    @Column(name = "coach_user_id")
    private UUID coachUserId;

    /** COACH_COACH : les deux coachs, ordonnés pour que (a,b) et (b,a) soient le même fil. */
    @Column(name = "peer_a_user_id")
    private UUID peerAUserId;

    @Column(name = "peer_b_user_id")
    private UUID peerBUserId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id")
    private TrainingGroup group;

    @Column(name = "dedup_key", nullable = false, length = 120)
    private String dedupKey;

    /** Dénormalisé : la boîte de réception trie là-dessus sans relire les messages. */
    @Column(name = "last_message_at")
    private Instant lastMessageAt;

    // --- Identité d'un fil, en un seul endroit ------------------------------------------------

    public static String athleteCoachKey(UUID athleteId, UUID coachUserId) {
        return "AC:" + athleteId + ":" + coachUserId;
    }

    /** L'ordre des deux coachs ne doit pas décider de l'existence d'un second fil. */
    public static String coachCoachKey(UUID a, UUID b) {
        return a.compareTo(b) <= 0 ? "CC:" + a + ":" + b : "CC:" + b + ":" + a;
    }

    public static String groupKey(UUID groupId) {
        return "G:" + groupId;
    }

    public static String clubKey(UUID clubId) {
        return "C:" + clubId;
    }
}
