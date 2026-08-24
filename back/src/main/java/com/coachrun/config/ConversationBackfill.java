package com.coachrun.config;

import com.coachrun.entity.Conversation;
import com.coachrun.entity.Message;
import com.coachrun.repository.CoachAthleteRelationRepository;
import com.coachrun.repository.MessageRepository;
import com.coachrun.service.ConversationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Rattache les messages antérieurs au modèle de conversations.
 *
 * <p>Avant, un message n'appartenait qu'à un athlète. Il faut donc décider, une fois, <b>de quel
 * binôme</b> relève chaque échange passé : c'est le coach <b>référent</b> de l'athlète, seul lien
 * durable entre eux. Un message écrit par un autre coach y est rattaché lui aussi — il a bien été
 * envoyé, et l'effacer serait pire que le déplacer ; il apparaît sous son nom d'auteur, qui a
 * toujours été porté par le message.</p>
 *
 * <p>Idempotent, et volontairement en Java plutôt qu'en SQL de migration : il lui faut la règle du
 * référent, qui vit dans le modèle, et un identifiant de fil que les deux moteurs (PostgreSQL en
 * production, H2 en test) ne génèrent pas de la même façon.</p>
 */
@Slf4j
@Component
@Order(60)
@RequiredArgsConstructor
public class ConversationBackfill implements ApplicationRunner {

    private final MessageRepository messageRepository;
    private final CoachAthleteRelationRepository relationRepository;
    private final ConversationService conversations;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        List<Message> orphans = messageRepository.findByConversationIsNull();
        if (orphans.isEmpty()) {
            return;
        }
        Map<UUID, Conversation> byAthlete = new HashMap<>();
        Map<UUID, Instant> lastAt = new HashMap<>();
        int attached = 0;
        int skipped = 0;

        for (Message m : orphans) {
            if (m.getAthlete() == null) {
                skipped++;
                continue;
            }
            UUID athleteId = m.getAthlete().getId();
            Conversation conversation = byAthlete.get(athleteId);
            if (conversation == null) {
                UUID referent = relationRepository
                        .findByAthleteIdAndReferentTrueAndActiveTrue(athleteId)
                        .map(r -> r.getCoach().getId())
                        .orElse(null);
                if (referent == null) {
                    // Sans référent, aucun binôme ne peut être désigné. Le message reste en
                    // place, sans fil : il n'est perdu pour personne, et le backfill du modèle
                    // multi-coach (qui tourne avant) crée justement ces relations.
                    skipped++;
                    continue;
                }
                conversation = conversations.athleteCoach(athleteId, referent);
                byAthlete.put(athleteId, conversation);
            }
            m.setConversation(conversation);
            Instant previous = lastAt.get(conversation.getId());
            if (previous == null || m.getCreatedAt().isAfter(previous)) {
                lastAt.put(conversation.getId(), m.getCreatedAt());
            }
            attached++;
        }
        byAthlete.values().forEach(c -> c.setLastMessageAt(lastAt.get(c.getId())));

        log.info("Backfill conversations : {} message(s) rattaché(s) à {} fil(s){}",
                attached, byAthlete.size(),
                skipped > 0 ? ", " + skipped + " sans binôme identifiable" : "");
    }
}
