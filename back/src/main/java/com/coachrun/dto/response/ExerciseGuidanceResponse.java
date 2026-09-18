package com.coachrun.dto.response;

import com.coachrun.entity.PpExercise;

import java.util.UUID;

/**
 * Le « comment on fait » d'un exercice, tel que l'athlète en a besoin au moment de le faire :
 * démonstration vidéo, consignes d'exécution, points techniques et contre-indications.
 *
 * <p><b>Pourquoi ce n'est pas dans la prescription.</b> Le snapshot d'une séance planifiée est
 * figé — c'est un contrat : ce qui a été prescrit ce jour-là ne doit pas changer sous les pieds de
 * l'athlète. La démonstration, elle, n'est pas un contrat mais une référence : une vidéo corrigée
 * ou une consigne précisée doit profiter aux séances <b>déjà</b> planifiées. Elle se lit donc en
 * direct depuis la bibliothèque du club, et non dans la copie gelée.</p>
 *
 * <p>Volontairement pauvre : ni catégorie, ni matériel, ni compteurs. On sert ce qui aide à
 * exécuter le mouvement, pas la fiche de gestion de la bibliothèque.</p>
 */
public record ExerciseGuidanceResponse(
        UUID id,
        String name,
        String videoUrl,
        String imageUrl,
        String instructions,
        String technicalNotes,
        String contraindications
) {

    public static ExerciseGuidanceResponse from(PpExercise e) {
        return new ExerciseGuidanceResponse(e.getId(), e.getName(), e.getVideoUrl(), e.getImageUrl(),
                e.getInstructions(), e.getTechnicalNotes(), e.getContraindications());
    }
}
