package com.coachrun.service;

import com.coachrun.dto.response.NotificationResponse;
import com.coachrun.dto.response.PageResponse;
import com.coachrun.entity.Notification;
import com.coachrun.exception.NotFoundException;
import com.coachrun.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/** Lecture du centre de notifications (scopé par l'utilisateur du token). */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserNotificationService {

    private final NotificationRepository notificationRepository;

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
    }

    @Transactional
    public void markAllRead(UUID userId) {
        notificationRepository.markAllRead(userId, Instant.now());
    }
}
