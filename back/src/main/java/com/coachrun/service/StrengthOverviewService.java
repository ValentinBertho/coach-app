package com.coachrun.service;

import com.coachrun.dto.response.StrengthOverviewRow;
import com.coachrun.entity.Athlete1rmProfile;
import com.coachrun.entity.StrengthLoadTracking;
import com.coachrun.entity.StrengthTest;
import com.coachrun.repository.Athlete1rmProfileRepository;
import com.coachrun.repository.StrengthLoadTrackingRepository;
import com.coachrun.repository.StrengthTestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Vue d'ensemble force de plusieurs athlètes : trois requêtes en tout, quel que soit le nombre
 * d'athlètes (une par athlète ferait ramer l'écran d'un club de 40 coureurs).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StrengthOverviewService {

    static final int WINDOW_DAYS = 28;

    private final Athlete1rmProfileRepository profileRepository;
    private final StrengthTestRepository testRepository;
    private final StrengthLoadTrackingRepository loadRepository;
    private final ClockService clockService;

    /** {@code athleteIds} est déjà filtré par le contrôleur sur ce que le coach a le droit de lire. */
    public List<StrengthOverviewRow> overview(Collection<UUID> athleteIds) {
        if (athleteIds.isEmpty()) return List.of();
        LocalDate today = clockService.today();
        LocalDate start4w = today.minusDays(WINDOW_DAYS - 1L);
        LocalDate startPrev = start4w.minusDays(WINDOW_DAYS);

        Map<UUID, List<Athlete1rmProfile>> profiles = profileRepository.findByAthleteIdIn(athleteIds).stream()
                .collect(Collectors.groupingBy(p -> p.getAthlete().getId()));
        Map<UUID, List<StrengthTest>> tests = testRepository.findByAthleteIdIn(athleteIds).stream()
                .collect(Collectors.groupingBy(t -> t.getAthlete().getId()));
        Map<UUID, List<StrengthLoadTracking>> loads = loadRepository
                .findByAthleteIdInAndSessionDateBetween(athleteIds, startPrev, today).stream()
                .collect(Collectors.groupingBy(l -> l.getAthlete().getId()));

        return athleteIds.stream().map(id -> {
            List<Athlete1rmProfile> p = profiles.getOrDefault(id, List.of());
            Instant last1rm = p.stream().map(Athlete1rmProfile::getUpdatedAt)
                    .filter(java.util.Objects::nonNull).max(Instant::compareTo).orElse(null);
            LocalDate lastTest = tests.getOrDefault(id, List.of()).stream().map(StrengthTest::getTestDate)
                    .filter(java.util.Objects::nonNull).max(LocalDate::compareTo).orElse(null);
            List<StrengthLoadTracking> l = loads.getOrDefault(id, List.of());
            List<StrengthLoadTracking> recent = l.stream().filter(x -> !x.getSessionDate().isBefore(start4w)).toList();
            List<StrengthLoadTracking> prev = l.stream().filter(x -> x.getSessionDate().isBefore(start4w)).toList();
            return new StrengthOverviewRow(id, p.size(), last1rm, lastTest,
                    recent.size(), prev.size(), sum(recent), sum(prev));
        }).toList();
    }

    private static BigDecimal sum(List<StrengthLoadTracking> rows) {
        return rows.stream().map(StrengthLoadTracking::getMechanicalLoad)
                .filter(java.util.Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
