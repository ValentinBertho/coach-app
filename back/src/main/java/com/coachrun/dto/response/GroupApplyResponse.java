package com.coachrun.dto.response;

/**
 * Résultat de l'application en masse d'un plan ou d'un mésocycle à un groupe :
 * nombre d'athlètes traités, ignorés (accès en écriture refusé) et séances créées au total.
 */
public record GroupApplyResponse(int athletes, int skipped, int created) {
}
