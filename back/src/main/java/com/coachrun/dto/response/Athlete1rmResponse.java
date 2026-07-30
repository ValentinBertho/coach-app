package com.coachrun.dto.response;

import com.coachrun.entity.Athlete1rmProfile;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * 1RM courant d'un athlète pour un exercice. Porte le nom de l'exercice : le profil de force
 * est lu depuis des écrans où la bibliothèque d'exercices n'est pas forcément chargée en entier
 * (elle est paginée), et un UUID tronqué n'est pas une information exploitable.
 */
public record Athlete1rmResponse(
        UUID exerciseId,
        String exerciseName,
        BigDecimal rmKg,
        String source
) {

    public static Athlete1rmResponse from(Athlete1rmProfile p) {
        return from(p, null);
    }

    public static Athlete1rmResponse from(Athlete1rmProfile p, String exerciseName) {
        return new Athlete1rmResponse(p.getExerciseId(), exerciseName, p.getRmKg(), p.getSource());
    }
}
