package com.coachrun.scheduler;

import com.coachrun.entity.User;
import com.coachrun.entity.enums.UserRole;
import com.coachrun.entity.enums.UserStatus;
import com.coachrun.repository.UserRepository;
import com.coachrun.service.ModerationQueueService;
import com.coachrun.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Prévient l'équipe, une fois par jour, de ce qui attend dans les trois files d'arbitrage.
 *
 * <p>Sans lui, les files sont muettes : rien ne dit qu'elles contiennent du travail, et une fiche
 * signalée pour faux diplôme reste publiée jusqu'à ce que quelqu'un pense à ouvrir le back-office.
 * Le dispositif de recours du lot 7 n'a de valeur que si sa boîte de réception est relevée.</p>
 *
 * <p><b>Rien n'est envoyé quand les files sont vides</b>, et c'est ce qui rend ce message lisible :
 * un digest quotidien qui arrive tous les jours en disant « rien » cesse d'être ouvert au bout
 * d'une semaine, et le jour où il dit quelque chose, il est classé avec les autres.</p>
 *
 * <p>7 h 30, juste après le digest d'alertes des coachs : c'est la même heure de lecture, et
 * l'équipe plateforme est souvent aussi coach.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ModerationDigestScheduler {

    private final ModerationQueueService queueService;
    private final NotificationService notificationService;
    private final UserRepository userRepository;

    @Scheduled(cron = "${app.moderation.digest-cron:0 30 7 * * *}")
    @SchedulerLock(name = "sendModerationDigest", lockAtLeastFor = "PT5M", lockAtMostFor = "PT15M")
    @Transactional
    public void sendModerationDigest() {
        ModerationQueueService.ModerationSummary summary = queueService.summary();
        if (!summary.hasWork()) {
            return;
        }

        List<User> admins = userRepository.findByRoleAndStatus(
                UserRole.PLATFORM_ADMIN, UserStatus.ACTIVE);
        if (admins.isEmpty()) {
            // Ni erreur ni silence : une plateforme sans administrateur actif a des files que
            // personne ne peut arbitrer, et c'est le genre de situation qui ne se remarque que le
            // jour où un signalement grave arrive.
            log.warn("Files de modération non vides ({} dossier(s)) mais aucun administrateur actif",
                    summary.total());
            return;
        }

        for (User admin : admins) {
            notificationService.notifyModerationQueue(admin, summary);
        }
        log.info("Digest de modération envoyé à {} administrateur(s) : {} dossier(s) en attente",
                admins.size(), summary.total());
    }
}
