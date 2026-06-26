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
        return new NotificationPreferencesResponse(u.isNotifyEmailEnabled(), u.isNotifyPushEnabled());
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
        return new NotificationPreferencesResponse(u.isNotifyEmailEnabled(), u.isNotifyPushEnabled());
    }
}
