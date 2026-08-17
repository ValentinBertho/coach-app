package com.coachrun.engine;

import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

/**
 * La semaine en une phrase : ce que la charge <b>prévue</b> fera à l'athlète, dite avant qu'il ne
 * la vive.
 *
 * <h2>Le manque qu'il comble</h2>
 *
 * <p>{@link PlannedLoadEngine} calcule déjà la charge prévue de chaque séance, et le calendrier
 * l'additionne par semaine. Cette somme n'était jamais confrontée à la charge chronique de
 * l'athlète : l'ACWR alertait le lundi suivant, sur une semaine déjà courue. L'information existait
 * <i>avant</i>, elle n'était simplement pas lue.</p>
 *
 * <p>Deux signaux, et pas un de plus : l'écart à l'habituel, et l'entassement des séances dures.
 * Un troisième chiffre ferait de cette phrase un tableau de bord — exactement ce qu'on cherche à
 * éviter.</p>
 *
 * <p>Logique pure, testable sans contexte Spring.</p>
 */
@Component
public class WeekOutlookEngine {

    /** Une séance prévue, réduite à ce qui compte pour juger la semaine. */
    public record PlannedSession(LocalDate date, Integer loadUa, Integer rpe) {
    }

    /** Gravité de la semaine, pour la pastille. */
    public enum Level { CALM, NORMAL, WATCH, HIGH }

    /**
     * @param plannedLoadUa    somme des charges prévues de la semaine
     * @param chronicWeeklyUa  charge chronique hebdomadaire de l'athlète (moyenne 28 j)
     * @param deltaPct         écart à l'habituel, en %, ou {@code null} sans historique
     * @param projectedAcwr    ACWR qu'atteindrait la semaine si elle était courue telle quelle
     * @param hardSessions     nombre de séances dures prévues
     * @param sentence         la phrase, affichable telle quelle dans la colonne de totaux
     */
    public record WeekOutlook(
            int plannedLoadUa, double chronicWeeklyUa, Integer deltaPct,
            Double projectedAcwr, int hardSessions, Level level, String sentence) {

        public static WeekOutlook empty() {
            return new WeekOutlook(0, 0, null, null, 0, Level.CALM, null);
        }
    }

    /** RPE prescrit à partir duquel une séance compte comme dure. */
    private static final int HARD_RPE = 7;
    /** Deux séances dures à moins de ce nombre de jours d'écart s'entassent. */
    private static final int HARD_MIN_GAP_DAYS = 2;

    /** Au-delà, la semaine sort de la bande de sécurité et mérite d'être dite. */
    private static final double ACWR_WATCH = 1.3;
    private static final double ACWR_HIGH = 1.5;

    /**
     * @param sessions        séances prévues de la semaine
     * @param chronicWeeklyUa charge chronique hebdomadaire, 0 si inconnue
     * @param chronicReady    faux tant que l'historique ne permet pas de publier un ratio — on
     *                        affiche alors la charge sans la comparer, plutôt qu'un écart faux
     */
    public WeekOutlook compute(List<PlannedSession> sessions, double chronicWeeklyUa,
                               boolean chronicReady) {
        if (sessions == null || sessions.isEmpty()) {
            return WeekOutlook.empty();
        }
        int total = sessions.stream()
                .mapToInt(s -> s.loadUa() == null ? 0 : s.loadUa())
                .sum();
        List<LocalDate> hardDays = sessions.stream()
                .filter(s -> s.rpe() != null && s.rpe() >= HARD_RPE)
                .map(PlannedSession::date)
                .sorted()
                .toList();
        int hard = hardDays.size();

        if (total <= 0) {
            return new WeekOutlook(0, chronicWeeklyUa, null, null, hard, Level.CALM, null);
        }
        if (!chronicReady || chronicWeeklyUa <= 0) {
            // Sans charge chronique fiable, on énonce le fait sans le juger. Un « +340 % » sur un
            // athlète qui vient de commencer serait faux, et le premier chiffre faux discrédite
            // tous les suivants.
            return new WeekOutlook(total, chronicWeeklyUa, null, null, hard, Level.NORMAL,
                    total + " UA prévues" + (hard > 0 ? " · " + hard + " séance" + s(hard) + " dure" + s(hard) : "") + ".");
        }

        int deltaPct = (int) Math.round((total / chronicWeeklyUa - 1) * 100);
        double projected = round(total / chronicWeeklyUa);
        boolean crowded = crowded(hardDays);

        Level level = projected >= ACWR_HIGH ? Level.HIGH
                : projected >= ACWR_WATCH || crowded ? Level.WATCH
                : projected < 0.8 ? Level.CALM
                : Level.NORMAL;

        return new WeekOutlook(total, round(chronicWeeklyUa), deltaPct, projected, hard, level,
                sentence(total, deltaPct, projected, hard, crowded, level));
    }

    /** Deux séances dures séparées de moins de deux jours : la semaine s'entasse. */
    private boolean crowded(List<LocalDate> hardDays) {
        for (int i = 1; i < hardDays.size(); i++) {
            if (java.time.temporal.ChronoUnit.DAYS.between(hardDays.get(i - 1), hardDays.get(i))
                    < HARD_MIN_GAP_DAYS) {
                return true;
            }
        }
        return false;
    }

    private String sentence(int total, int deltaPct, double projected, int hard,
                            boolean crowded, Level level) {
        StringBuilder sb = new StringBuilder();
        sb.append(total).append(" UA · ");
        sb.append(deltaPct >= 0 ? "+" : "").append(deltaPct).append(" % vs habituel");
        if (level == Level.HIGH || level == Level.WATCH) {
            sb.append(" · ACWR projeté ").append(fr(projected));
        }
        sb.append('.');
        if (crowded && hard > 1) {
            sb.append(' ').append(hard).append(" séances dures rapprochées.");
        } else if (level == Level.CALM && deltaPct <= -25) {
            sb.append(" Semaine de décharge.");
        }
        return sb.toString();
    }

    private String s(int n) {
        return n > 1 ? "s" : "";
    }

    /** Virgule décimale : le produit parle français partout ailleurs. */
    private String fr(double v) {
        return String.valueOf(v).replace('.', ',');
    }

    private double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
