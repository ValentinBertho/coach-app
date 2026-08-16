package com.coachrun.service;

import com.coachrun.dto.response.ActivityLapsResponse;
import com.coachrun.engine.PhysioDetectionEngine;
import com.coachrun.engine.PhysioDetectionEngine.Detection;
import com.coachrun.engine.PhysioDetectionEngine.Effort;
import com.coachrun.entity.Activity;
import com.coachrun.entity.Athlete;
import com.coachrun.entity.AthleteProposal;
import com.coachrun.entity.enums.ProposalType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * « Ton niveau a bougé » : ce qu'une sortie importée dit du profil de l'athlète.
 *
 * <h2>Le manque qu'il comble</h2>
 *
 * <p>Le VDOT ne bougeait que si un humain saisissait un chrono. À côté, chaque activité importée
 * arrivait avec ses tours, son flux et sa distance — et n'alimentait jamais le profil. Un athlète
 * qui court un 10 km en compétition et synchronise sa montre gardait ses allures de travail d'il y a
 * trois mois, alors que la cascade de recalcul n'attendait qu'un chiffre.</p>
 *
 * <h2>La validation du coach n'est pas une formalité</h2>
 *
 * <p>Ce service ne met <b>jamais</b> un profil à jour. Il crée une proposition, et c'est le coach
 * qui tranche — parce que lui seul sait si cet effort était une performance de référence, un
 * fractionné en descente, ou une sortie avec un lièvre. Une valeur détectée n'a pas le statut d'une
 * valeur mesurée : c'est la règle qui vaut déjà pour le 1RM, et elle vaut ici pour la même
 * raison.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PhysioDetectionService {

    private final ActivityService activityService;
    private final PhysioDetectionEngine engine;
    private final ProposalService proposalService;

    /**
     * Examine une activité fraîchement importée et propose, s'il y a lieu, une nouvelle référence
     * de niveau.
     *
     * @return la proposition créée, ou {@code null} — le cas de loin le plus fréquent, et c'est
     *         voulu : une proposition par sortie transformerait la file du coach en bruit
     */
    @Transactional
    public AthleteProposal inspect(Activity activity) {
        if (activity == null || activity.getDistanceM() == null || activity.getDurationS() == null) {
            return null;
        }
        Athlete athlete = activity.getAthlete();
        Double currentVdot = athlete.getVdot() == null ? null : athlete.getVdot().doubleValue();

        Detection detection = engine.detect(efforts(activity), currentVdot);
        if (detection == null || detection.distance() == null) {
            // Sans distance de référence, on ne saurait pas quoi enregistrer : un effort de 11,4 km
            // inscrit comme « 10 km » fausserait aussi les records de l'athlète.
            return null;
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("distance", detection.distance().name());
        payload.put("timeSeconds", detection.timeSeconds());
        payload.put("date", activity.getActivityDate().toString());
        payload.put("vdot", detection.vdot());

        AthleteProposal p = proposalService.propose(athlete, ProposalType.PHYSIO_UPDATE,
                activity.getId(), activity.getActivityDate(),
                detection.title(), detection.reason(), payload,
                "physio:" + activity.getId(), false);
        if (p != null) {
            log.info("Niveau détecté sur l'activité {} : VDOT {} (athlète {})",
                    activity.getId(), detection.vdot(), athlete.getId());
        }
        return p;
    }

    /**
     * Les efforts candidats d'une sortie : la sortie entière, et les blocs continus de ses tours.
     *
     * <p>La sortie entière couvre le cas d'une course. Les tours cumulés couvrent le cas d'un test
     * de terrain — un 3 000 m ou un 5 000 m posé au milieu d'un entraînement, que la montre a
     * découpé.</p>
     */
    private List<Effort> efforts(Activity activity) {
        List<Effort> out = new ArrayList<>();
        out.add(new Effort(activity.getDistanceM(), activity.getDurationS()));

        try {
            ActivityLapsResponse laps = activityService.laps(
                    activity.getClub().getId(), activity.getId(), ActivityLapsResponse.Kind.DEVICE);
            if (laps != null) {
                for (var lap : laps.laps()) {
                    if (lap.distanceM() != null && lap.durationS() != null) {
                        out.add(new Effort(lap.distanceM(), lap.durationS()));
                    }
                }
            }
        } catch (RuntimeException e) {
            log.debug("Tours illisibles pour l'activité {}", activity.getId());
        }
        return out;
    }
}
