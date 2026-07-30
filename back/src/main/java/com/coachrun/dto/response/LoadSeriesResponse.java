package com.coachrun.dto.response;

import java.time.LocalDate;
import java.util.List;

/**
 * Série temporelle de charge d'entraînement : un point par jour, avec la charge aiguë (ATL,
 * 7 jours glissants), la charge chronique (CTL, 28 jours ramenés à la semaine) et le ratio ACWR.
 *
 * <p>Les écrans n'affichaient que des valeurs instantanées : impossible de voir si la charge
 * monte, redescend ou si l'ACWR vient de sortir de sa bande de sécurité. Mêmes conventions de
 * calcul que {@link LoadResponse} — c'est le même moteur, historisé jour par jour.</p>
 */
public record LoadSeriesResponse(
        LocalDate from,
        LocalDate to,
        /** Bornes de la bande de sécurité ACWR, pour que le client n'ait pas à les redéfinir. */
        double safeRatioMin,
        double safeRatioMax,
        List<Point> points) {

    public record Point(LocalDate date, double acute, double chronic, Double ratio) {
    }
}
