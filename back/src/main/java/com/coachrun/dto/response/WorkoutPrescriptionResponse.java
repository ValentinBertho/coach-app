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
        /**
         * Notes de la séance (intention, consignes) telles que l'athlète les lit. Recopiées du
         * modèle à l'assignation, puis propres à cette séance : l'éditeur d'adaptation les
         * affiche et les réécrit sans toucher à la bibliothèque.
         */
        String notes,
        /** Effort attendu sur la séance entière (1–10), nul quand rien n'est annoncé. */
        Integer targetRpe,
        SessionStructure snapshot,
        CalculatedSessionResponse calculated,
        /** Éducatifs (gammes) référencés par les blocs, résolus pour l'affichage (nom, vidéo). */
        java.util.List<RunDrillResponse> drills,
        /**
         * La prescription enregistrée n'a pas pu être relue. Le {@code snapshot} est alors vide,
         * mais ce n'est pas une séance sans contenu : l'écran doit le dire au lieu d'afficher une
         * séance vide, que le coach prendrait pour un oubli et l'athlète pour un repos.
         */
        boolean unreadable
) {
}
