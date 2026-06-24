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
import com.coachrun.security.AuthPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    // --- Côté coach (scopé club) ---
    public List<MessageResponse> coachThread(UUID clubId, UUID athleteId) {
        return messageRepository.findByClubIdAndAthleteIdOrderByCreatedAtAsc(clubId, athleteId)
                .stream().map(MessageResponse::from).toList();
    }

    @Transactional
    public MessageResponse coachSend(UUID clubId, UUID athleteId, AuthPrincipal principal, MessageRequest request) {
        Athlete athlete = athleteRepository.findByIdAndClubId(athleteId, clubId)
                .orElseThrow(() -> new NotFoundException("Athlète introuvable."));
        return MessageResponse.from(persist(athlete, principal, request));
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
        Athlete athlete = athleteRepository.findByIdAndClubId(athleteId, clubId)
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

        com.coachrun.entity.MessageAttachment att = new com.coachrun.entity.MessageAttachment();
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
