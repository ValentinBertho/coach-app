package com.coachrun.repository;

import com.coachrun.entity.Conversation;
import com.coachrun.entity.enums.ConversationKind;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ConversationRepository extends JpaRepository<Conversation, UUID> {

    /** Identité d'un fil : c'est par là qu'on ouvre une conversation sans jamais la dupliquer. */
    Optional<Conversation> findByDedupKey(String dedupKey);

    /** Fils de binôme d'un coach (sa boîte de réception athlète par athlète). */
    List<Conversation> findByCoachUserId(UUID coachUserId);

    /** Fils de binôme d'un athlète : un par coach qui lui a écrit. */
    List<Conversation> findByAthleteId(UUID athleteId);

    /** Fils entre coachs auxquels cet utilisateur participe. */
    List<Conversation> findByPeerAUserIdOrPeerBUserId(UUID a, UUID b);

    List<Conversation> findByClubIdAndKind(UUID clubId, ConversationKind kind);

    Optional<Conversation> findByGroupId(UUID groupId);
}
