package com.coachrun.dto.response;

import java.util.UUID;

/**
 * Alerte actionnable du tableau de bord coach : un athlète qui nécessite une attention
 * (douleur, charge à risque, séances manquées, silence). Triées par gravité côté service.
 *
 * @param severity {@code RED} (critique) ou {@code ORANGE} (à surveiller)
 * @param type     code stable de la règle déclenchée (PAIN, ACWR_HIGH, ACWR_LOW, MONOTONY, MISSED, SILENCE)
 * @param action   ce que le coach peut faire d'ici, sans changer d'écran — ou {@code null} quand
 *                 l'alerte n'appelle que de la lecture
 */
public record CoachAlertResponse(
        UUID athleteId,
        String athleteName,
        String discipline,
        String severity,
        String type,
        String title,
        String detail,
        SuggestedAction action) {

    /**
     * L'action recommandée, attachée à l'alerte qui la justifie.
     *
     * <h2>Pourquoi elle vit ici</h2>
     *
     * <p>L'écran du matin affichait quatre listes — un excellent inventaire, dont chaque ligne
     * demandait d'ouvrir un autre écran pour agir. La ligne « Marc, douleur 6/10 » disait le
     * problème et laissait le coach chercher le geste. Attacher le geste à la ligne est la
     * différence entre une liste de problèmes et une file de décisions.</p>
     *
     * <p>L'action ne s'exécute jamais d'elle-même : {@code code} dit à l'interface quel bouton
     * afficher, et le bouton appelle une route explicite. Une alerte n'est pas un ordre.</p>
     *
     * @param code   REVIEW_TOMORROW | RESCHEDULE_WEEK | REVIEW_LOAD | MESSAGE | OPEN_ATHLETE
     * @param label  le libellé du bouton, écrit pour être lu tel quel
     * @param link   la route de destination quand l'action est une navigation
     */
    public record SuggestedAction(String code, String label, String link) {
    }

    /** Alerte sans action attachée — la lecture suffit. */
    public static CoachAlertResponse of(UUID athleteId, String athleteName, String discipline,
                                        String severity, String type, String title, String detail) {
        return new CoachAlertResponse(athleteId, athleteName, discipline, severity, type, title,
                detail, null);
    }
}
