package com.coachrun.dto.response;

import com.coachrun.dto.session.SessionStructure;

/**
 * Prescription figée d'une séance planifiée : snapshot des blocs (au moment de l'assignation)
 * et cibles calculées pour l'athlète.
 */
public record WorkoutPrescriptionResponse(
        /**
         * Titre de la séance planifiée, tel que l'athlète le voit.
         *
         * <p>Porté ici plutôt que par un second appel : l'éditeur d'adaptation charge déjà cette
         * réponse, et il lui manquait précisément de quoi renommer ce qu'il modifie — un coach qui
         * transforme un « 4 × 1000 » en six répétitions se retrouvait avec une séance dont le nom
         * décrit l'ancienne.</p>
         */
        String title,
        SessionStructure snapshot,
        CalculatedSessionResponse calculated,
        /** Éducatifs (gammes) référencés par les blocs, résolus pour l'affichage (nom, vidéo). */
        java.util.List<RunDrillResponse> drills
) {
}
