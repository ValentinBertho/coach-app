package com.coachrun.service;

import com.coachrun.engine.VdotEngine;
import com.coachrun.entity.Activity;
import com.coachrun.repository.ActivityRepository;
import com.coachrun.repository.AthleteVdotPaceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Allure d'endurance de référence d'un athlète (s/km) — celle qui sert à donner un ordre de
 * grandeur de volume quand une séance n'est écrite qu'en durée.
 *
 * <p><b>Deux sources, dans cet ordre, et rien d'autre.</b> D'abord l'allure d'endurance
 * fondamentale dérivée de son VDOT : c'est la référence que le produit calcule déjà et que le
 * coach lit pour prescrire. À défaut — un athlète sans performance de référence n'a pas de VDOT —
 * la moyenne de ses dernières sorties réelles. Si aucune des deux n'existe, on rend {@code null} :
 * un repère tiré d'une valeur moyenne du monde serait une donnée inventée sur le dos de
 * l'athlète, et fausserait son volume prévu dans un sens qu'il ne pourrait pas soupçonner.</p>
 *
 * <p>Le pendant côté écran est {@code PaceReferenceService} : les deux doivent résoudre la même
 * allure, sans quoi le volume affiché et le volume compté ne concorderaient pas.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EndurancePaceService {

    /** Nombre de sorties récentes moyennées quand aucun VDOT n'est connu. */
    private static final int RECENT_RUNS = 10;
    /** Fenêtre de recherche de ces sorties : au-delà, elles ne décrivent plus l'athlète d'aujourd'hui. */
    private static final int RECENT_DAYS = 90;

    /** Bornes d'une allure de course à pied ; hors de là, la valeur est une aberration de mesure. */
    private static final int FASTEST_S_PER_KM = 120;
    private static final int SLOWEST_S_PER_KM = 900;

    private final AthleteVdotPaceRepository vdotPaceRepository;
    private final ActivityRepository activityRepository;
    private final VdotEngine vdotEngine;
    private final ClockService clock;

    /** Allure d'endurance de l'athlète, ou {@code null} si rien ne permet de la déduire. */
    public Integer referencePace(UUID athleteId) {
        Integer fromVdot = vdotPaceRepository.findByAthleteId(athleteId)
                .map(p -> p.getVdot() == null ? null
                        : vdotEngine.easyPaceSecPerKm(p.getVdot().doubleValue()))
                .filter(EndurancePaceService::plausible)
                .orElse(null);
        return fromVdot != null ? fromVdot : fromRecentRuns(athleteId);
    }

    /**
     * Allure moyenne des dernières sorties, calculée sur les <b>totaux</b> (distance cumulée sur
     * temps cumulé) : moyenner des allures donnerait autant de poids à un footing de 4 km qu'à une
     * sortie longue de 25.
     */
    private Integer fromRecentRuns(UUID athleteId) {
        LocalDate today = clock.today();
        List<Activity> recent = activityRepository
                .findByAthleteIdAndActivityDateBetween(athleteId, today.minusDays(RECENT_DAYS), today);

        long distanceM = 0;
        long durationS = 0;
        int used = 0;
        for (Activity a : recent) {
            if (used >= RECENT_RUNS) {
                break;
            }
            if (a.getDistanceM() != null && a.getDurationS() != null
                    && a.getDistanceM() > 0 && a.getDurationS() > 0) {
                distanceM += a.getDistanceM();
                durationS += a.getDurationS();
                used++;
            }
        }
        if (distanceM <= 0 || durationS <= 0) {
            return null;
        }
        int pace = (int) Math.round(durationS * 1000.0 / distanceM);
        return plausible(pace) ? pace : null;
    }

    private static boolean plausible(Integer pace) {
        return pace != null && pace >= FASTEST_S_PER_KM && pace <= SLOWEST_S_PER_KM;
    }
}
