package com.coachrun.service;

import com.coachrun.dto.request.MessageRequest;
import com.coachrun.dto.response.MessageResponse;
import com.coachrun.entity.Athlete;
import com.coachrun.entity.Message;
import com.coachrun.entity.User;
import com.coachrun.exception.NotFoundException;
import com.coachrun.repository.AthleteRepository;
import com.coachrun.repository.MessageRepository;
import com.coachrun.repository.UserRepository;
import com.coachrun.security.AthleteAccessValidator;
import com.coachrun.security.AuthPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Messagerie : écriture des messages, pièces jointes et quota.
 *
 * <p>Le <b>cloisonnement</b>, lui, appartient à {@link ConversationService} : ce service ne décide
 * jamais qui lit quoi, il écrit dans le fil qu'on lui désigne.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MessageService {

    private final MessageRepository messageRepository;
    private final AthleteRepository athleteRepository;
    private final UserRepository userRepository;
    private final MessageStreamService streamService;
    private final NotificationService notificationService;
    private final com.coachrun.repository.MessageAttachmentRepository attachmentRepository;

    /** Quota de stockage par club (pièces jointes de messagerie). */
    @org.springframework.beans.factory.annotation.Value("${app.storage.club-quota-mb:200}")
    private int storageQuotaMb;

    private long storageQuotaBytes() {
        return storageQuotaMb * 1024L * 1024L;
    }

    private final AthleteAccessValidator accessValidator;
    private final ConversationService conversationService;
    private final com.coachrun.repository.CoachAthleteRelationRepository relationRepository;

    // --- Côté coach (scopé club) ---
    /**
     * Fil de CE coach avec cet athlète, borné aux {@code limit} messages les plus récents (rendus
     * dans l'ordre chronologique). Le fil entier était chargé à chaque ouverture : sans borne, une
     * conversation qui dure une saison finit par transporter des centaines de messages.
     *
     * <p>En écriture malgré son nom : la première ouverture crée le fil du binôme, et une
     * transaction en lecture seule le laisserait en mémoire sans jamais l'écrire.</p>
     */
    @Transactional
    public List<MessageResponse> coachThread(UUID clubId, UUID athleteId, AuthPrincipal principal, int limit) {
        var conversation = conversationService.athleteCoach(athleteId, principal.userId());
        return conversationService.messages(principal, conversation.getId(), limit);
    }

    @Transactional
    public MessageResponse coachSend(UUID clubId, UUID athleteId, AuthPrincipal principal, MessageRequest request) {
        Athlete athlete = athleteRepository.findByIdAndClubMembership(athleteId, clubId)
                .orElseThrow(() -> new NotFoundException("Athlète introuvable."));
        return MessageResponse.from(persist(athlete,
                conversationService.athleteCoach(athleteId, principal.userId()), principal, request));
    }

    /**
     * Écrire dans un fil, quelle qu'en soit la nature.
     *
     * <p>Le contrôle d'accès est fait une fois, ici, par le service des conversations : un fil en
     * lecture seule — celui du club pour un athlète — refuse l'écriture, et un fil auquel on ne
     * participe pas reste « introuvable ».</p>
     */
    @Transactional
    public MessageResponse postToConversation(AuthPrincipal principal, UUID conversationId,
                                              MessageRequest request) {
        com.coachrun.entity.Conversation conversation =
                conversationService.requireWritable(principal, conversationId);
        User sender = userRepository.findById(principal.userId())
                .orElseThrow(() -> new NotFoundException("Expéditeur introuvable."));

        Message m = new Message();
        m.setClub(conversation.getClub());
        m.setAthlete(conversation.getAthlete());
        m.setConversation(conversation);
        m.setSenderUserId(sender.getId());
        m.setSenderRole(sender.getRole());
        m.setSenderName(sender.getFullName());
        m.setBody(request.body());
        // Une séance ne se rattache qu'à un fil qui parle d'un athlète : ailleurs, le lien
        // ouvrirait une séance que ses lecteurs n'ont pas le droit de voir.
        m.setWorkoutId(conversation.getAthlete() == null ? null : request.workoutId());
        Message saved = messageRepository.save(m);
        conversation.setLastMessageAt(saved.getCreatedAt());
        deliver(conversation, saved);
        return MessageResponse.from(saved);
    }

    /**
     * Accusé de lecture : le coach a ouvert le fil de ce binôme.
     *
     * <p>« Lu » n'est plus un attribut du message — il ne voulait déjà plus rien dire dès qu'un
     * second coach lisait le même tas — mais du couple (fil, personne).</p>
     */
    @Transactional
    public void markThreadRead(UUID athleteId, AuthPrincipal principal) {
        var conversation = conversationService.athleteCoach(athleteId, principal.userId());
        conversationService.markRead(principal, conversation.getId());
    }

    // --- Côté athlète (scopé athleteId du principal) ---
    /** Fil de l'athlète, même bornage que le fil du coach. */
    public List<MessageResponse> athleteThread(AuthPrincipal principal, int limit) {
        return conversationService.defaultAthleteConversation(principal.athleteId())
                .map(c -> conversationService.messages(principal, c.getId(), limit))
                .orElseGet(List::of);
    }

    /** Profondeur par défaut d'un fil : de quoi couvrir plusieurs semaines d'échanges. */
    public static final int DEFAULT_THREAD_LIMIT = 100;

    static int threadLimit(int requested) {
        return Math.max(1, Math.min(requested <= 0 ? DEFAULT_THREAD_LIMIT : requested, 500));
    }

    @Transactional
    public MessageResponse athleteSend(UUID athleteId, AuthPrincipal principal, MessageRequest request) {
        Athlete athlete = athleteRepository.findById(athleteId)
                .orElseThrow(() -> new NotFoundException("Athlète introuvable."));
        return MessageResponse.from(persist(athlete, athleteDefaultConversation(athlete), principal, request));
    }

    /**
     * Le fil qu'ouvre un athlète depuis son espace : celui qu'il a le plus récemment utilisé, et à
     * défaut celui de son coach référent.
     *
     * <p>Un athlète n'a pas à choisir un destinataire pour répondre à son coach. S'il veut écrire à
     * un autre, il passe par « Nouveau message », qui ouvre explicitement le fil de ce binôme.</p>
     */
    private com.coachrun.entity.Conversation athleteDefaultConversation(Athlete athlete) {
        return conversationService.defaultAthleteConversation(athlete.getId())
                .orElseGet(() -> conversationService.athleteCoach(athlete.getId(),
                        referentCoachId(athlete)));
    }

    private UUID referentCoachId(Athlete athlete) {
        return relationRepository.findByAthleteIdAndReferentTrueAndActiveTrue(athlete.getId())
                .map(r -> r.getCoach().getId())
                .orElseThrow(() -> new NotFoundException("Aucun coach référent pour cet athlète."));
    }

    Message persist(Athlete athlete, com.coachrun.entity.Conversation conversation,
                    AuthPrincipal principal, MessageRequest request) {
        User sender = userRepository.findById(principal.userId())
                .orElseThrow(() -> new NotFoundException("Expéditeur introuvable."));
        Message m = new Message();
        m.setClub(athlete.getClub());
        m.setAthlete(athlete);
        m.setConversation(conversation);
        m.setSenderUserId(sender.getId());
        m.setSenderRole(sender.getRole());
        m.setSenderName(sender.getFullName());
        m.setBody(request.body());
        m.setWorkoutId(request.workoutId());
        Message saved = messageRepository.save(m);
        conversation.setLastMessageAt(saved.getCreatedAt());
        deliver(conversation, saved);
        return saved;
    }

    /**
     * Remise d'un message : temps réel pour qui regarde déjà le fil, notification pour les autres.
     *
     * <p>La messagerie ne se reposait que sur le flux SSE, qui n'atteint que les clients ayant
     * l'écran ouvert — c'est-à-dire ceux qui n'ont besoin de rien. Un athlète qui posait une
     * question le soir n'apprenait la réponse de son coach qu'en rouvrant l'application.</p>
     */
    private void deliver(com.coachrun.entity.Conversation conversation, Message saved) {
        // Le flux temps réel est indexé par FIL et non plus par athlète : deux coachs qui suivent
        // le même athlète ne doivent pas voir passer les messages de l'autre.
        streamService.broadcast(conversation.getId(), MessageResponse.from(saved));
        notificationService.notifyNewMessage(saved);
    }

    // --- Pièces jointes ---

    private static final java.util.Set<String> ALLOWED_TYPES = java.util.Set.of(
            "image/png", "image/jpeg", "image/gif", "image/webp", "application/pdf");

    @Transactional
    public MessageResponse coachSendWithAttachment(UUID clubId, UUID athleteId, AuthPrincipal principal,
                                                   String body, org.springframework.web.multipart.MultipartFile file) {
        Athlete athlete = athleteRepository.findByIdAndClubMembership(athleteId, clubId)
                .orElseThrow(() -> new NotFoundException("Athlète introuvable."));
        return MessageResponse.from(persistWithAttachment(athlete,
                conversationService.athleteCoach(athleteId, principal.userId()), principal, body, file));
    }

    @Transactional
    public MessageResponse athleteSendWithAttachment(UUID athleteId, AuthPrincipal principal,
                                                     String body, org.springframework.web.multipart.MultipartFile file) {
        Athlete athlete = athleteRepository.findById(athleteId)
                .orElseThrow(() -> new NotFoundException("Athlète introuvable."));
        return MessageResponse.from(persistWithAttachment(athlete, athleteDefaultConversation(athlete),
                principal, body, file));
    }

    Message persistWithAttachment(Athlete athlete, com.coachrun.entity.Conversation conversation,
                                  AuthPrincipal principal, String body,
                                  org.springframework.web.multipart.MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new com.coachrun.exception.ApiException(
                    org.springframework.http.HttpStatus.BAD_REQUEST, "Fichier manquant.");
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_TYPES.contains(contentType)) {
            throw new com.coachrun.exception.ApiException(
                    org.springframework.http.HttpStatus.BAD_REQUEST, "Type de fichier non autorisé (image ou PDF).");
        }
        User sender = userRepository.findById(principal.userId())
                .orElseThrow(() -> new NotFoundException("Expéditeur introuvable."));

        requireStorageQuota(athlete.getClub(), file.getSize());

        com.coachrun.entity.MessageAttachment att = new com.coachrun.entity.MessageAttachment();
        att.setClub(athlete.getClub());
        att.setFilename(sanitize(file.getOriginalFilename()));
        att.setContentType(contentType);
        att.setSizeBytes(file.getSize());
        try {
            att.setData(file.getBytes());
        } catch (java.io.IOException e) {
            throw new com.coachrun.exception.ApiException(
                    org.springframework.http.HttpStatus.BAD_REQUEST, "Lecture du fichier impossible.");
        }
        att = attachmentRepository.save(att);

        Message m = new Message();
        m.setClub(athlete.getClub());
        m.setAthlete(athlete);
        m.setConversation(conversation);
        m.setSenderUserId(sender.getId());
        m.setSenderRole(sender.getRole());
        m.setSenderName(sender.getFullName());
        m.setBody(body == null || body.isBlank() ? att.getFilename() : body);
        m.setAttachmentId(att.getId());
        m.setAttachmentFilename(att.getFilename());
        m.setAttachmentContentType(att.getContentType());
        Message saved = messageRepository.save(m);
        conversation.setLastMessageAt(saved.getCreatedAt());
        deliver(conversation, saved);
        return saved;
    }

    /**
     * Quota de stockage du club. Les pièces jointes vivent en {@code bytea} : sans plafond, ce
     * sont le {@code pg_dump} quotidien et les artefacts de CI qui cassent en premier — bien avant
     * que quiconque remarque le volume.
     */
    private void requireStorageQuota(com.coachrun.entity.Club club, long incomingBytes) {
        if (club == null) {
            return; // coaching privé sans club : rien à plafonner pour l'instant
        }
        long used = attachmentRepository.totalSizeBytesByClub(club.getId());
        if (used + incomingBytes > storageQuotaBytes()) {
            throw new com.coachrun.exception.ApiException(
                    org.springframework.http.HttpStatus.PAYLOAD_TOO_LARGE,
                    "Espace de stockage du club saturé (" + mb(used) + " Mo utilisés sur "
                            + mb(storageQuotaBytes()) + " Mo). Supprimez d'anciennes pièces jointes "
                            + "ou contactez le support.");
        }
    }

    /** Espace de stockage consommé et disponible pour un club. */
    public com.coachrun.dto.response.StorageUsageResponse storageUsage(UUID clubId) {
        return new com.coachrun.dto.response.StorageUsageResponse(
                attachmentRepository.totalSizeBytesByClub(clubId), storageQuotaBytes());
    }

    private long mb(long bytes) {
        return Math.round(bytes / (1024.0 * 1024.0));
    }

    /** Pièce jointe (octets) après contrôle d'accès via le message porteur. */
    public com.coachrun.entity.MessageAttachment attachmentForCoach(UUID clubId, UUID athleteId, UUID messageId) {
        Message m = messageRepository.findById(messageId)
                .orElseThrow(() -> new NotFoundException("Message introuvable."));
        if (!m.getClub().getId().equals(clubId) || !m.getAthlete().getId().equals(athleteId)) {
            throw new NotFoundException("Message introuvable.");
        }
        return loadAttachment(m);
    }

    public com.coachrun.entity.MessageAttachment attachmentForAthlete(UUID athleteId, UUID messageId) {
        Message m = messageRepository.findById(messageId)
                .orElseThrow(() -> new NotFoundException("Message introuvable."));
        if (!m.getAthlete().getId().equals(athleteId)) {
            throw new NotFoundException("Message introuvable.");
        }
        return loadAttachment(m);
    }

    private com.coachrun.entity.MessageAttachment loadAttachment(Message m) {
        if (m.getAttachmentId() == null) {
            throw new NotFoundException("Pièce jointe introuvable.");
        }
        return attachmentRepository.findById(m.getAttachmentId())
                .orElseThrow(() -> new NotFoundException("Pièce jointe introuvable."));
    }

    private String sanitize(String name) {
        if (name == null || name.isBlank()) {
            return "fichier";
        }
        return name.replaceAll("[\\r\\n\"\\\\/]", "_");
    }
}
