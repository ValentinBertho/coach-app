package com.coachrun.service;

import com.coachrun.dto.response.MailLogResponse;
import com.coachrun.dto.response.MailStatsResponse;
import com.coachrun.dto.response.MailStatsResponse.MailDayResponse;
import com.coachrun.dto.response.MailStatsResponse.MailKindResponse;
import com.coachrun.dto.response.PageResponse;
import com.coachrun.entity.MailLog;
import com.coachrun.entity.enums.MailKind;
import com.coachrun.repository.MailLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Consommation d'e-mails : ce que l'administrateur regarde pour savoir s'il approche d'un plafond.
 *
 * <p><b>Pourquoi des plafonds configurables.</b> Ils dépendent du plan souscrit chez le
 * fournisseur, pas du code. Les valeurs par défaut sont celles du plan gratuit de Resend — cent
 * envois par jour, trois mille par mois —, celles-là mêmes qui ont motivé le basculement des
 * notifications de routine vers le push. Renseigner zéro masque simplement la jauge.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MailStatsService {

    private final MailLogRepository repository;
    private final ClockService clock;

    @Value("${app.mail.quota.daily:100}")
    private long dailyQuota;

    @Value("${app.mail.quota.monthly:3000}")
    private long monthlyQuota;

    /** Fenêtre par défaut de l'histogramme : un mois, la période sur laquelle porte le plafond. */
    public static final int DEFAULT_WINDOW_DAYS = 30;

    public MailStatsResponse stats(int days) {
        int window = Math.max(7, Math.min(days <= 0 ? DEFAULT_WINDOW_DAYS : days, 90));
        LocalDate today = clock.today();
        Instant startOfDay = today.atStartOfDay(clock.zone()).toInstant();
        Instant startOfMonth = today.withDayOfMonth(1).atStartOfDay(clock.zone()).toInstant();
        Instant since = today.minusDays(window - 1L).atStartOfDay(clock.zone()).toInstant();

        return new MailStatsResponse(
                repository.countBySentAtAfterAndSentTrue(startOfDay),
                dailyQuota,
                repository.countBySentAtAfterAndSentTrue(startOfMonth),
                monthlyQuota,
                repository.countBySentAtAfterAndSentFalse(
                        today.minusDays(6).atStartOfDay(clock.zone()).toInstant()),
                dailyVolume(since, today, window),
                byKind(since));
    }

    /**
     * Histogramme quotidien, <b>jours vides compris</b>. Un histogramme qui saute les jours sans
     * envoi raconte une histoire fausse : trois barres côte à côte laissent croire à trois jours
     * consécutifs alors qu'il peut s'en être écoulé dix.
     */
    private List<MailDayResponse> dailyVolume(Instant since, LocalDate today, int window) {
        Map<LocalDate, long[]> perDay = new HashMap<>();
        for (MailLog log : repository.findBySentAtAfterOrderBySentAtAsc(since)) {
            LocalDate day = log.getSentAt().atZone(clock.zone()).toLocalDate();
            long[] counts = perDay.computeIfAbsent(day, d -> new long[2]);
            if (log.isSent()) {
                counts[0]++;
            } else {
                counts[1]++;
            }
        }
        List<MailDayResponse> out = new ArrayList<>(window);
        for (int i = window - 1; i >= 0; i--) {
            LocalDate day = today.minusDays(i);
            long[] counts = perDay.getOrDefault(day, new long[2]);
            out.add(new MailDayResponse(day, counts[0], counts[1]));
        }
        return out;
    }

    private List<MailKindResponse> byKind(Instant since) {
        List<MailKindResponse> out = new ArrayList<>();
        for (Object[] row : repository.volumeByKind(since)) {
            MailKind kind = (MailKind) row[0];
            out.add(new MailKindResponse(kind.name(), kind.label(), kind.isTransactional(),
                    ((Number) row[1]).longValue()));
        }
        return out;
    }

    /** Les derniers envois, du plus récent au plus ancien : le diagnostic individuel. */
    public PageResponse<MailLogResponse> log(Pageable pageable) {
        return PageResponse.from(repository.findAllByOrderBySentAtDesc(pageable),
                MailLogResponse::from);
    }
}
