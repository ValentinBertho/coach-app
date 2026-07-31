package com.coachrun.service;

import com.coachrun.dto.request.MessageRequest;
import com.coachrun.dto.response.ConversationResponse;
import com.coachrun.dto.response.MessageResponse;
import com.coachrun.entity.Athlete;
import com.coachrun.entity.Message;
import com.coachrun.entity.User;
import com.coachrun.entity.enums.UserRole;
import com.coachrun.exception.NotFoundException;
import com.coachrun.repository.AthleteRepository;
import com.coachrun.repository.MessageRepository;
import com.coachrun.repository.UserRepository;
import com.coachrun.security.AthleteAccessValidator;
import com.coachrun.security.AuthPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Messagerie coach ↔ athlète (fil par athlète). */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MessageService {

    private final MessageRepository messageRepository;
    private final AthleteRepository athleteRepository;
    private final UserRepository userRepository;
    private final MessageStreamService streamService;
    private final com.coachrun.repository.MessageAttachmentRepository attachmentRepository;

    /** Quota de stockage par club (pièces jointes de messagerie). */
    @org.springframework.beans.factory.annotation.Value("${app.storage.club-quota-mb:200}")
    private int storageQuotaMb;

    private long storageQuotaBytes() {
        return storageQuotaMb * 1024L * 1024L;
    }

    private final AthleteAccessValidator accessValidator;

    // --- Côté coach (scopé club) ---
    public List<MessageResponse> coachThread(UUID clubId, UUID athleteId) {
        return messageRepository.findByClubIdAndAthleteIdOrderByCreatedAtAsc(clubId, athleteId)
                .stream().map(MessageResponse::from).toList();
    }

    @Transactional
    public MessageResponse coachSend(UUID clubId, UUID athleteId, AuthPrincipal principal, MessageRequest request) {
        Athlete athlete = athleteRepository.findByIdAndClubMembership(athleteId, clubId)
                .orElseThrow(() -> new NotFoundException("Athlète introuvable."));
        return MessageResponse.from(persist(athlete, principal, request));
    }

    // --- Boîte de réception coach (agrégat tous athlètes) ---

    /**
     * Conversations du coach : un fil par athlète de son périmètre ayant au moins un message,
     * trié du plus récent au plus ancien, avec le compteur de non-lus.
     *
     * <p>Le périmètre est réévalué athlète par athlète : un coach ne voit jamais une
     * conversation d'un athlète auquel il n'a pas accès.</p>
     */
    public List<ConversationResponse> conversations(UUID clubId, UUID coachId) {
        List<ConversationResponse> out = new ArrayList<>();
        for (Athlete a : athleteRepository.findByClubIdOrderByLastNameAsc(clubId)) {
            if (accessValidator.effectiveLevel(coachId, a.getId()).isEmpty()) {
                continue;
            }
            Message last = messageRepository
                    .findFirstByClubIdAndAthleteIdOrderByCreatedAtDesc(clubId, a.getId())
                    .orElse(null);
            if (last == null) {
                continue;
            }
            long unread = messageRepository
                    .countByClubIdAndAthleteIdAndSenderRoleAndCoachReadAtIsNull(clubId, a.getId(), UserRole.ATHLETE);
            out.add(new ConversationResponse(
                    a.getId(), a.getFirstName(), a.getLastName(),
                    last.getBody(), last.getSenderRole().name(), last.getCreatedAt(), unread));
        }
        out.sort(java.util.Comparator.comparing(ConversationResponse::lastMessageAt).reversed());
        return out;
    }

    /**
     * Total de messages non lus du coach, tous athlètes confondus (badge de la navigation).
     * Une seule requête agrégée : le badge se rafraîchit à chaque changement d'écran, il ne
     * peut pas parcourir tout le club fil par fil.
     */
    public long unreadCount(UUID clubId, UUID coachId) {
        long total = 0;
        for (Object[] row : messageRepository.countUnreadByAthlete(clubId, UserRole.ATHLETE)) {
            UUID athleteId = (UUID) row[0];
            if (accessValidator.effectiveLevel(coachId, athleteId).isPresent()) {
                total += (Long) row[1];
            }
        }
        return total;
    }

    /** Accusé de lecture : le coach a ouvert le fil de cet athlète. */
    @Transactional
    public void markThreadRead(UUID clubId, UUID athleteId) {
        messageRepository.markThreadRead(clubId, athleteId, UserRole.ATHLETE, java.time.Instant.now());
    }

    // --- Côté athlète (scopé athleteId du principal) ---
    public List<MessageResponse> athleteThread(UUID athleteId) {
        return messageRepository.findByAthleteIdOrderByCreatedAtAsc(athleteId)
                .stream().map(MessageResponse::from).toList();
    }

    @Transactional
    public MessageResponse athleteSend(UUID athleteId, AuthPrincipal principal, MessageRequest request) {
        Athlete athlete = athleteRepository.findById(athleteId)
                .orElseThrow(() -> new NotFoundException("Athlète introuvable."));
        return MessageResponse.from(persist(athlete, principal, request));
    }

    private Message persist(Athlete athlete, AuthPrincipal principal, MessageRequest request) {
        User sender = userRepository.findById(principal.userId())
                .orElseThrow(() -> new NotFoundException("Expéditeur introuvable."));
        Message m = new Message();
        m.setClub(athlete.getClub());
        m.setAthlete(athlete);
        m.setSenderUserId(sender.getId());
        m.setSenderRole(sender.getRole());
        m.setSenderName(sender.getFullName());
        m.setBody(request.body());
        m.setWorkoutId(request.workoutId());
        Message saved = messageRepository.save(m);
        streamService.broadcast(athlete.getId(), MessageResponse.from(saved));
        return saved;
    }

    // --- Pièces jointes ---

    private static final java.util.Set<String> ALLOWED_TYPES = java.util.Set.of(
            "image/png", "image/jpeg", "image/gif", "image/webp", "application/pdf");

    @Transactional
    public MessageResponse coachSendWithAttachment(UUID clubId, UUID athleteId, AuthPrincipal principal,
                                                   String body, org.springframework.web.multipart.MultipartFile file) {
        Athlete athlete = athleteRepository.findByIdAndClubMembership(athleteId, clubId)
                .orElseThrow(() -> new NotFoundException("Athlète introuvable."));
        return MessageResponse.from(persistWithAttachment(athlete, principal, body, file));
    }

    @Transactional
    public MessageResponse athleteSendWithAttachment(UUID athleteId, AuthPrincipal principal,
                                                     String body, org.springframework.web.multipart.MultipartFile file) {
        Athlete athlete = athleteRepository.findById(athleteId)
                .orElseThrow(() -> new NotFoundException("Athlète introuvable."));
        return MessageResponse.from(persistWithAttachment(athlete, principal, body, file));
    }

    private Message persistWithAttachment(Athlete athlete, AuthPrincipal principal, String body,
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
        m.setSenderUserId(sender.getId());
        m.setSenderRole(sender.getRole());
        m.setSenderName(sender.getFullName());
        m.setBody(body == null || body.isBlank() ? att.getFilename() : body);
        m.setAttachmentId(att.getId());
        m.setAttachmentFilename(att.getFilename());
        m.setAttachmentContentType(att.getContentType());
        Message saved = messageRepository.save(m);
        streamService.broadcast(athlete.getId(), MessageResponse.from(saved));
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
