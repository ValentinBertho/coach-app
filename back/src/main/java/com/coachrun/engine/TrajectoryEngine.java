package com.coachrun.engine;

import org.springframework.stereotype.Component;

/**
 * La trajectoire : où en est l'athlète de son objectif, et pourquoi.
 *
 * <h2>Le manque qu'il comble</h2>
 *
 * <p>{@code RaceObjective} porte une date, une distance et un chrono visé. {@link VdotEngine} sait
 * déduire un VDOT d'une performance et l'inverser pour prédire un temps. Les deux n'avaient jamais
 * été mis face à face : la donnée la plus motivante du produit — l'objectif — ne servait à rien, et
 * rien ne reliait l'effort du jour au but de la saison.</p>
 *
 * <h2>Ce qu'il calcule</h2>
 *
 * <p>Le VDOT qu'exigerait l'objectif, celui que l'athlète a aujourd'hui, l'écart en secondes, et la
 * question qui compte vraiment : cet écart est-il rattrapable au rythme où l'athlète progresse
 * déjà&nbsp;? La pente vient de son propre historique, jamais d'une table générique — deux athlètes
 * ne progressent pas au même rythme, et celui qui débute progresse vite.</p>
 *
 * <p>Logique pure, testable sans contexte Spring.</p>
 */
@Component
public class TrajectoryEngine {

    /** Comment se présente l'objectif. */
    public enum Outlook {
        /** La projection est déjà meilleure que l'objectif. */
        AHEAD,
        /** L'écart se comble au rythme de progression observé. */
        ON_TRACK,
        /** L'écart ne se comble pas au rythme actuel, mais reste plausible. */
        AT_RISK,
        /** L'écart demande un saut que l'historique ne rend pas crédible. */
        OUT_OF_REACH,
        /** Pas assez d'éléments pour dire quoi que ce soit. */
        UNKNOWN
    }

    /**
     * @param requiredVdot   VDOT qu'exige le chrono visé sur cette distance
     * @param currentVdot    VDOT actuel de l'athlète
     * @param projectedTimeS temps prédit aujourd'hui sur la distance de la course
     * @param gapSeconds     écart au chrono visé (positif = il manque du temps à gagner)
     * @param vdotGap        points de VDOT manquants (positif = il en manque)
     * @param vdotPerWeek    progression observée, en points de VDOT par semaine, ou {@code null}
     * @param weeksLeft      semaines restantes avant la course
     * @param headline       la phrase principale, affichable telle quelle
     * @param detail         ce qui explique la situation — la ligne qui rend la première utile
     */
    public record Trajectory(
            Outlook outlook,
            Double requiredVdot, Double currentVdot,
            Integer projectedTimeS, Integer gapSeconds, Double vdotGap,
            Double vdotPerWeek, int weeksLeft,
            String headline, String detail) {

        public static Trajectory unknown(String headline, String detail, int weeksLeft) {
            return new Trajectory(Outlook.UNKNOWN, null, null, null, null, null, null,
                    weeksLeft, headline, detail);
        }
    }

    private final VdotEngine vdotEngine;

    public TrajectoryEngine(VdotEngine vdotEngine) {
        this.vdotEngine = vdotEngine;
    }

    /**
     * @param currentVdot     VDOT courant, ou {@code null} si l'athlète n'a aucune performance
     * @param distanceMeters  distance de la course
     * @param targetTimeS     chrono visé, ou {@code null} si l'athlète n'en a pas déclaré
     * @param weeksLeft       semaines avant la course (0 si elle est imminente)
     * @param vdotPerWeek     progression observée sur l'historique, ou {@code null}
     */
    public Trajectory compute(Double currentVdot, int distanceMeters, Integer targetTimeS,
                              int weeksLeft, Double vdotPerWeek) {
        if (currentVdot == null || currentVdot <= 0 || distanceMeters <= 0) {
            return Trajectory.unknown("Pas encore de projection.",
                    "Enregistre un chrono de référence et ta projection apparaîtra ici.", weeksLeft);
        }

        int projected = vdotEngine.racePaceSecPerKm(currentVdot, distanceMeters)
                * distanceMeters / 1000;
        // L'allure d'équivalence est arrondie à la seconde par kilomètre : sur un marathon, cet
        // arrondi vaut une trentaine de secondes. On repasse donc par le temps exact.
        projected = (int) Math.round(vdotEngine.timeForVdot(currentVdot, distanceMeters));

        if (targetTimeS == null || targetTimeS <= 0) {
            return new Trajectory(Outlook.UNKNOWN, null, round(currentVdot), projected, null, null,
                    vdotPerWeek, weeksLeft,
                    "Projection : " + formatTime(projected) + ".",
                    "Aucun chrono visé n'est déclaré — ajoute-le pour suivre l'écart.");
        }

        double requiredVdot = vdotEngine.vdot(distanceMeters, targetTimeS);
        int gap = projected - targetTimeS;
        double vdotGap = requiredVdot - currentVdot;

        if (gap <= 0) {
            return new Trajectory(Outlook.AHEAD, round(requiredVdot), round(currentVdot),
                    projected, gap, round(vdotGap), vdotPerWeek, weeksLeft,
                    "Projection " + formatTime(projected) + " — objectif dépassé de "
                            + formatDuration(-gap) + ".",
                    "Ton niveau actuel place déjà la course sous l'objectif. À confirmer le jour J.");
        }

        String headline = "Projection " + formatTime(projected) + " — il manque "
                + formatDuration(gap) + ".";

        if (vdotPerWeek == null || vdotPerWeek <= 0 || weeksLeft <= 0) {
            // Sans pente observée, on ne prétend pas savoir. Dire « atteignable » sur la foi de
            // rien serait pire que de se taire.
            return new Trajectory(Outlook.AT_RISK, round(requiredVdot), round(currentVdot),
                    projected, gap, round(vdotGap), vdotPerWeek, weeksLeft,
                    headline,
                    "Il faudrait gagner " + round(vdotGap) + " points de VDOT"
                            + (weeksLeft > 0 ? " en " + weeksLeft + " semaines." : "."));
        }

        double reachable = vdotPerWeek * weeksLeft;
        String detail = "Il faudrait gagner " + round(vdotGap) + " points de VDOT en "
                + weeksLeft + " semaines ; tu en as pris " + round(vdotPerWeek * weeksLeft)
                + " sur une durée équivalente.";

        Outlook outlook = reachable >= vdotGap ? Outlook.ON_TRACK
                : reachable >= vdotGap * 0.6 ? Outlook.AT_RISK
                : Outlook.OUT_OF_REACH;
        return new Trajectory(outlook, round(requiredVdot), round(currentVdot),
                projected, gap, round(vdotGap), round(vdotPerWeek), weeksLeft, headline, detail);
    }

    /**
     * Progression observée, en points de VDOT par semaine, entre le plus ancien et le meilleur
     * VDOT de l'historique.
     *
     * <p>Rendue {@code null} sous quatre semaines d'écart : sur une fenêtre plus courte, deux
     * performances suffisent à produire une pente délirante — et une pente délirante donne un
     * pronostic délirant.</p>
     */
    public Double weeklyProgress(double oldestVdot, double latestVdot, long daysBetween) {
        if (daysBetween < 28) {
            return null;
        }
        double delta = latestVdot - oldestVdot;
        if (delta <= 0) {
            return null;
        }
        return round(delta / (daysBetween / 7.0));
    }

    private double round(double v) {
        return Math.round(v * 10.0) / 10.0;
    }

    /** « 3 h 24 » ou « 41:20 ». */
    public static String formatTime(int seconds) {
        int h = seconds / 3600;
        int m = (seconds % 3600) / 60;
        int s = seconds % 60;
        return h > 0 ? String.format("%d h %02d", h, m) : String.format("%d:%02d", m, s);
    }

    /** « 9 min » ou « 45 s » — un écart se lit en unités rondes. */
    public static String formatDuration(int seconds) {
        if (seconds < 60) {
            return seconds + " s";
        }
        int m = seconds / 60;
        if (m < 60) {
            return m + " min";
        }
        return (m / 60) + " h " + String.format("%02d", m % 60);
    }
}
