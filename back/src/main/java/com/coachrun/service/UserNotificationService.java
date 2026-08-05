package com.coachrun.service;

import com.coachrun.dto.request.NotificationPreferencesRequest;
import com.coachrun.dto.response.NotificationPreferencesResponse;
import com.coachrun.dto.response.NotificationResponse;
import com.coachrun.dto.response.PageResponse;
import com.coachrun.entity.Notification;
import com.coachrun.entity.User;
import com.coachrun.exception.NotFoundException;
import com.coachrun.repository.NotificationRepository;
import com.coachrun.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.UUID;

/** Lecture du centre de notifications + préférences (scopé par l'utilisateur du token). */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserNotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final NotificationStreamService streamService;

    public PageResponse<NotificationResponse> list(UUID userId, Pageable pageable) {
        return PageResponse.from(
                notificationRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable),
                NotificationResponse::from);
    }

    public long unreadCount(UUID userId) {
        return notificationRepository.countByUserIdAndReadAtIsNull(userId);
    }

    @Transactional
    public void markRead(UUID userId, UUID notificationId) {
        Notification n = notificationRepository.findByIdAndUserId(notificationId, userId)
                .orElseThrow(() -> new NotFoundException("Notification introuvable."));
        if (n.getReadAt() == null) {
            n.setReadAt(Instant.now());
        }
        streamService.publishUnread(userId, notificationRepository.countByUserIdAndReadAtIsNull(userId));
    }

    @Transactional
    public void markAllRead(UUID userId) {
        notificationRepository.markAllRead(userId, Instant.now());
        streamService.publishUnread(userId, 0);
    }

    public NotificationPreferencesResponse preferences(UUID userId) {
        User u = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("Utilisateur introuvable."));
        return toResponse(u);
    }

    @Transactional
    public NotificationPreferencesResponse updatePreferences(UUID userId, NotificationPreferencesRequest req) {
        User u = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("Utilisateur introuvable."));
        if (req.emailEnabled() != null) {
            u.setNotifyEmailEnabled(req.emailEnabled());
        }
        if (req.pushEnabled() != null) {
            u.setNotifyPushEnabled(req.pushEnabled());
        }
        if (req.usualSessionTime() != null) {
            u.setUsualSessionTime(parseTime(req.usualSessionTime()));
        }
        // Remplacement, pas fusion : l'écran envoie l'état complet des cases, et une case
        // décochée doit pouvoir revenir à « non coupée ».
        if (req.mutedCategories() != null) {
            u.setMutedCategories(req.mutedCategories());
        }
        if (req.quietStart() != null) {
            u.setNotifyQuietStart(parseTime(req.quietStart()));
        }
        if (req.quietEnd() != null) {
            u.setNotifyQuietEnd(parseTime(req.quietEnd()));
        }
        return toResponse(u);
    }

    private NotificationPreferencesResponse toResponse(User u) {
        return new NotificationPreferencesResponse(u.isNotifyEmailEnabled(), u.isNotifyPushEnabled(),
                hhmm(u.getUsualSessionTime()), u.mutedCategories(),
                hhmm(u.getNotifyQuietStart()), hhmm(u.getNotifyQuietEnd()));
    }

    private String hhmm(LocalTime time) {
        return time == null ? null : time.format(DateTimeFormatter.ofPattern("HH:mm"));
    }

    /** « HH:mm » → heure ; chaîne vide (ou horaire illisible) = rappel de débriefing désactivé. */
    private LocalTime parseTime(String raw) {
        String value = raw.trim();
        if (value.isEmpty()) {
            return null;
        }
        try {
            return LocalTime.parse(value);
        } catch (DateTimeParseException ex) {
            throw new com.coachrun.exception.ApiException(org.springframework.http.HttpStatus.BAD_REQUEST,
                    "Heure habituelle de séance invalide (format attendu HH:mm).");
        }
    }
}
