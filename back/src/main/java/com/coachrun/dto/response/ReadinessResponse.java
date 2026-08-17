package com.coachrun.dto.response;

import com.coachrun.engine.SessionAdaptationEngine.Advice;
import com.coachrun.entity.enums.FormStatus;

import java.util.List;
import java.util.UUID;

/**
 * Le feu vert du matin, tel que l'athlète le lit après ses trois curseurs.
 *
 * <p>Le champ qui compte est {@code advice} : il dit ce que le produit conseille. Les champs
 * {@code sessionTitle} / {@code adaptedSummary} donnent de quoi comparer <b>avant</b> de demander
 * quoi que ce soit — un athlète n'accepte pas une adaptation qu'il ne peut pas voir.</p>
 *
 * @param advice         la conduite conseillée
 * @param severity       couleur de la pastille, dérivée de fatigue + douleur (jamais du RPE)
 * @param title          la phrase principale
 * @param reason         les chiffres qui l'expliquent, ou {@code null} quand tout va bien
 * @param workoutId      la séance concernée, s'il y en a une
 * @param sessionTitle   son nom
 * @param plannedSummary ce qui est prévu, en une ligne (« 6 × 1000 m »)
 * @param adaptedSummary ce que donnerait l'adaptation (« 4 × 1000 m »), ou {@code null}
 * @param pendingRequest vrai si une demande est déjà partie — on ne la redemande pas
 * @param checkInDone    l'athlète a-t-il rempli son check-in aujourd'hui
 */
public record ReadinessResponse(
        Advice advice,
        FormStatus severity,
        String title,
        String reason,
        UUID workoutId,
        String sessionTitle,
        String plannedSummary,
        String adaptedSummary,
        boolean pendingRequest,
        boolean checkInDone,
        List<String> availableActions
) {

    public static ReadinessResponse nothingPlanned(boolean checkInDone) {
        return new ReadinessResponse(Advice.KEEP, FormStatus.GREEN, "Repos aujourd'hui.", null,
                null, null, null, null, false, checkInDone, List.of());
    }
}
