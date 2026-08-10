package com.coachrun.dto.response;

import java.util.List;

/**
 * Courbe d'une sortie : fréquence cardiaque et allure le long de la distance parcourue.
 *
 * <p>Les moyennes d'une séance disent ce qu'elle a coûté ; la courbe dit <b>comment</b> elle s'est
 * passée — la dérive cardiaque d'une sortie longue, l'échauffement qui monte, les dents de scie
 * d'un fractionné. C'est la lecture que l'athlète cherche en premier quand il rouvre sa séance,
 * et la seule que ni le bandeau de stats ni le tableau des tours ne donnent.</p>
 *
 * <p>L'abscisse est la <b>distance</b>, pas le temps : sur un axe temporel les récupérations
 * s'écrasent et les répétitions s'étirent, et le coureur ne reconnaît plus sa séance.</p>
 *
 * @param points          échantillons dans l'ordre, déjà ramenés à une taille affichable
 * @param totalDistanceM  distance couverte par la courbe (m)
 * @param hasHeartRate    vrai si au moins un point porte une FC — sinon l'écran n'affiche pas d'axe
 * @param hasPace         vrai si au moins un point porte une allure
 */
public record ActivityStreamResponse(
        List<Point> points,
        int totalDistanceM,
        boolean hasHeartRate,
        boolean hasPace) {

    /**
     * Un échantillon de la courbe.
     *
     * @param distanceM  distance cumulée (m) — l'abscisse
     * @param elapsedS   temps écoulé (s), pour l'infobulle
     * @param hr         fréquence cardiaque (bpm), ou {@code null}
     * @param paceSPerKm allure instantanée (s/km), ou {@code null} (arrêt, valeur aberrante)
     */
    public record Point(int distanceM, int elapsedS, Integer hr, Integer paceSPerKm) {
    }

    public static ActivityStreamResponse empty() {
        return new ActivityStreamResponse(List.of(), 0, false, false);
    }
}
