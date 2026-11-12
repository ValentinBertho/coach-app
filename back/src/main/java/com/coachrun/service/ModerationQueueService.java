package com.coachrun.service;

import com.coachrun.entity.enums.ClubRequestStatus;
import com.coachrun.entity.enums.CoachProfileStatus;
import com.coachrun.entity.enums.CoachReportStatus;
import com.coachrun.repository.ClubCreationRequestRepository;
import com.coachrun.repository.CoachProfileReportRepository;
import com.coachrun.repository.CoachProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

/**
 * L'état des trois files d'arbitrage, en un objet.
 *
 * <h2>Le défaut que ce service ferme</h2>
 *
 * <p>Les trois files du back-office — demandes de club, fiches coachs, signalements — étaient
 * <b>passives</b> : rien ne prévenait qu'elles contenaient du travail. L'unique e-mail qui partait
 * sur une demande de club était un accusé de réception <em>au demandeur</em>, pas une alerte à
 * l'équipe. Un coach inscrit vendredi soir attendait donc qu'on pense à regarder.</p>
 *
 * <p>Le signalement a rendu ce silence coûteux : une fiche accusée de porter un faux diplôme reste
 * publiée tant que personne n'a ouvert la file. C'est le dispositif de recours entier qui perd son
 * sens si sa boîte de réception n'est jamais relevée.</p>
 */
@Service
@RequiredArgsConstructor
public class ModerationQueueService {

    private final ClubCreationRequestRepository clubRequestRepository;
    private final CoachProfileRepository profileRepository;
    private final CoachProfileReportRepository reportRepository;

    /**
     * Ce qui attend un arbitre, et depuis combien de temps.
     *
     * <p>{@code oldestReportAgeDays} n'est pas un ornement : c'est le seul chiffre qui distingue
     * une file qui tourne d'une file qui dérape. Trois signalements arrivés ce matin et trois qui
     * attendent depuis huit jours demandent la même chose de l'équipe et ne disent pas du tout la
     * même chose sur elle.</p>
     */
    public record ModerationSummary(long pendingClubRequests,
                                    long pendingCoachProfiles,
                                    long openReports,
                                    Long oldestReportAgeDays) {

        /** Vrai si quelque chose attend. Rien n'est envoyé sinon : un digest vide s'ignore vite. */
        public boolean hasWork() {
            return pendingClubRequests > 0 || pendingCoachProfiles > 0 || openReports > 0;
        }

        public long total() {
            return pendingClubRequests + pendingCoachProfiles + openReports;
        }
    }

    @Transactional(readOnly = true)
    public ModerationSummary summary() {
        long openReports = reportRepository.countByStatus(CoachReportStatus.OPEN);
        Long oldestReportAgeDays = reportRepository
                .findFirstByStatusOrderByCreatedAtAsc(CoachReportStatus.OPEN)
                .map(r -> Duration.between(r.getCreatedAt(), Instant.now()).toDays())
                .orElse(null);

        return new ModerationSummary(
                clubRequestRepository.countByStatus(ClubRequestStatus.PENDING),
                profileRepository.countByStatus(CoachProfileStatus.PENDING),
                openReports,
                oldestReportAgeDays);
    }
}
