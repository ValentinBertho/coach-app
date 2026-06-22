package com.coachrun.engine;

import java.util.Locale;

/**
 * Conversions d'allure : m/s ↔ secondes/km ↔ km/h, et formatage « m:ss/km ».
 * Utilitaire pur (sans dépendance Spring).
 */
public final class PaceUtil {

    private PaceUtil() {
    }

    /** Vitesse en m/s → allure en secondes par km. */
    public static double msToSecPerKm(double metersPerSecond) {
        return metersPerSecond <= 0 ? 0 : 1000.0 / metersPerSecond;
    }

    /** Allure en secondes/km → vitesse en m/s. */
    public static double secPerKmToMs(double secPerKm) {
        return secPerKm <= 0 ? 0 : 1000.0 / secPerKm;
    }

    /** Allure en secondes/km → vitesse en km/h. */
    public static double secPerKmToKmh(double secPerKm) {
        return secPerKm <= 0 ? 0 : 3600.0 / secPerKm;
    }

    /** Formate une allure en secondes/km sous la forme « 4:07 » (m:ss). */
    public static String formatPace(int secPerKm) {
        if (secPerKm <= 0) {
            return "—";
        }
        int minutes = secPerKm / 60;
        int seconds = secPerKm % 60;
        return String.format(Locale.ROOT, "%d:%02d", minutes, seconds);
    }
}
