package com.coachrun.engine;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * « Séance tenue&nbsp;? » — la confrontation, bloc par bloc, de ce qui était prescrit à ce qui a
 * été couru.
 *
 * <h2>Le manque qu'il comble</h2>
 *
 * <p>Une séance était validée sur son volume total : un 10 × 400 couru à l'allure du dimanche
 * comptait pour 100 %. Les deux moitiés de l'information dormaient pourtant dans la même base — les
 * fourchettes figées à l'assignation ({@code Workout.calculatedPaces}) d'un côté, les tours relevés
 * par la montre ({@code Activity.lapsJson}) de l'autre. Elles ne s'étaient jamais rencontrées.</p>
 *
 * <h2>L'appariement</h2>
 *
 * <p>La montre ne connaît pas la structure de la séance : elle rend une suite de tours, dont
 * certains sont des efforts et d'autres des récupérations. On apparie donc <b>dans l'ordre</b>, en
 * ne retenant comme candidats que les tours dont la distance ou la durée est du bon ordre de
 * grandeur — la récupération de 200 m entre deux 1000 m ne peut pas passer pour une répétition.</p>
 *
 * <p>Ce que le moteur refuse de faire : deviner. Un bloc sans fourchette calculable, ou sans tour
 * plausible en face, sort <b>non évalué</b> plutôt qu'échoué. Un score qui punit l'absence de
 * données est un score que personne ne croit.</p>
 *
 * <p>Logique pure, testable sans contexte Spring.</p>
 */
@Component
public class ComplianceEngine {

    /** Un effort prescrit : sa fourchette d'allure et son volume attendu. */
    public record PrescribedEffort(
            String label,
            Integer paceFastSecPerKm,
            Integer paceSlowSecPerKm,
            Integer distanceM,
            Integer durationS) {
    }

    /** Un tour réellement couru. */
    public record RealizedLap(int index, Integer distanceM, Integer durationS, Integer paceSecPerKm) {
    }

    /** Verdict d'un effort. */
    public enum Verdict {
        /** Dans la fourchette. */
        ON_TARGET,
        /** Plus rapide que la borne haute — souvent le vrai problème d'une séance ratée. */
        TOO_FAST,
        /** Plus lent que la borne basse. */
        TOO_SLOW,
        /** Pas de fourchette, ou pas de tour en face : rien à dire. */
        NOT_ASSESSED
    }

    /**
     * @param deltaSecPerKm écart signé à la fourchette (négatif = plus rapide), nul si non évalué
     */
    public record EffortResult(
            String label, Verdict verdict,
            Integer targetFastSecPerKm, Integer targetSlowSecPerKm,
            Integer actualPaceSecPerKm, Integer deltaSecPerKm) {
    }

    /**
     * @param scorePct       part des efforts évalués qui sont dans la fourchette, ou {@code null}
     *                       si rien n'était évaluable
     * @param assessed       nombre d'efforts confrontés
     * @param onTarget       nombre d'efforts dans la fourchette
     * @param verdictLabel   la phrase à afficher en haut de la fiche
     * @param detail         la ligne qui explique le score — dérive de fin, départ trop rapide…
     */
    public record SessionCompliance(
            Integer scorePct, int assessed, int onTarget,
            String verdictLabel, String detail,
            List<EffortResult> efforts) {

        public static SessionCompliance empty() {
            return new SessionCompliance(null, 0, 0, null, null, List.of());
        }
    }

    /** Tolérance sur la fourchette : une montre et un GPS ne mesurent pas au dixième de seconde. */
    private static final int TOLERANCE_S_PER_KM = 5;

    /** Un tour candidat doit peser au moins la moitié du volume attendu — sinon c'est une récup. */
    private static final double MIN_VOLUME_RATIO = 0.5;
    private static final double MAX_VOLUME_RATIO = 1.8;

    public SessionCompliance evaluate(List<PrescribedEffort> prescribed, List<RealizedLap> laps) {
        if (prescribed == null || prescribed.isEmpty() || laps == null || laps.isEmpty()) {
            return SessionCompliance.empty();
        }

        List<EffortResult> results = new ArrayList<>();
        int cursor = 0;
        int assessed = 0;
        int onTarget = 0;

        for (PrescribedEffort effort : prescribed) {
            int matched = nextPlausibleLap(laps, cursor, effort);
            if (matched < 0 || !hasRange(effort)) {
                results.add(new EffortResult(effort.label(), Verdict.NOT_ASSESSED,
                        effort.paceFastSecPerKm(), effort.paceSlowSecPerKm(), null, null));
                continue;
            }
            RealizedLap lap = laps.get(matched);
            cursor = matched + 1;

            Integer pace = lap.paceSecPerKm();
            if (pace == null || pace <= 0) {
                results.add(new EffortResult(effort.label(), Verdict.NOT_ASSESSED,
                        effort.paceFastSecPerKm(), effort.paceSlowSecPerKm(), null, null));
                continue;
            }

            int fast = effort.paceFastSecPerKm() - TOLERANCE_S_PER_KM;
            int slow = effort.paceSlowSecPerKm() + TOLERANCE_S_PER_KM;
            Verdict verdict;
            Integer delta = null;
            if (pace < fast) {
                verdict = Verdict.TOO_FAST;
                delta = pace - effort.paceFastSecPerKm();
            } else if (pace > slow) {
                verdict = Verdict.TOO_SLOW;
                delta = pace - effort.paceSlowSecPerKm();
            } else {
                verdict = Verdict.ON_TARGET;
                onTarget++;
            }
            assessed++;
            results.add(new EffortResult(effort.label(), verdict,
                    effort.paceFastSecPerKm(), effort.paceSlowSecPerKm(), pace, delta));
        }

        if (assessed == 0) {
            return new SessionCompliance(null, 0, 0, null, null, results);
        }
        int score = (int) Math.round(onTarget * 100.0 / assessed);
        return new SessionCompliance(score, assessed, onTarget,
                label(score, onTarget, assessed), detail(results), results);
    }

    /** La fourchette existe-t-elle&nbsp;? Sans elle il n'y a rien à confronter. */
    private boolean hasRange(PrescribedEffort e) {
        return e.paceFastSecPerKm() != null && e.paceSlowSecPerKm() != null
                && e.paceFastSecPerKm() > 0 && e.paceSlowSecPerKm() > 0;
    }

    /**
     * Premier tour, à partir de {@code from}, dont le volume est du bon ordre de grandeur.
     *
     * <p>Sans ce filtre, l'appariement séquentiel prendrait la récupération de 200 m pour la
     * deuxième répétition de 1000 m, puis décalerait tout le reste de la séance — et le score
     * s'effondrerait sur une séance parfaitement exécutée.</p>
     */
    private int nextPlausibleLap(List<RealizedLap> laps, int from, PrescribedEffort effort) {
        Integer targetDistance = effort.distanceM();
        Integer targetDuration = effort.durationS();
        for (int i = from; i < laps.size(); i++) {
            RealizedLap lap = laps.get(i);
            if (targetDistance != null && targetDistance > 0 && lap.distanceM() != null) {
                double ratio = lap.distanceM() / (double) targetDistance;
                if (ratio >= MIN_VOLUME_RATIO && ratio <= MAX_VOLUME_RATIO) {
                    return i;
                }
                continue;
            }
            if (targetDuration != null && targetDuration > 0 && lap.durationS() != null) {
                double ratio = lap.durationS() / (double) targetDuration;
                if (ratio >= MIN_VOLUME_RATIO && ratio <= MAX_VOLUME_RATIO) {
                    return i;
                }
                continue;
            }
            // Aucun volume prescrit exploitable : on prend le tour suivant, faute de mieux.
            return i;
        }
        return -1;
    }

    private String label(int score, int onTarget, int assessed) {
        String head = score >= 85 ? "Séance tenue"
                : score >= 60 ? "Séance partiellement tenue"
                : "Séance non tenue";
        return head + " à " + score + " %";
    }

    /**
     * La ligne qui transforme un pourcentage en information : <i>où</i> ça a lâché, et dans quel
     * sens. Un score sans cette phrase ne dit rien de plus qu'une courbe.
     */
    private String detail(List<EffortResult> results) {
        List<EffortResult> assessed = results.stream()
                .filter(r -> r.verdict() != Verdict.NOT_ASSESSED).toList();
        if (assessed.isEmpty()) {
            return null;
        }
        long tooFast = assessed.stream().filter(r -> r.verdict() == Verdict.TOO_FAST).count();
        long tooSlow = assessed.stream().filter(r -> r.verdict() == Verdict.TOO_SLOW).count();
        if (tooFast == 0 && tooSlow == 0) {
            return "Toutes les répétitions dans la fourchette.";
        }

        // Une dérive de fin ne se lit pas comme un départ trop rapide : c'est le même écart, mais
        // le premier dit « fin de séance en difficulté » et le second « parti trop vite ».
        int half = Math.max(1, assessed.size() / 2);
        long slowInLastHalf = assessed.subList(assessed.size() - half, assessed.size()).stream()
                .filter(r -> r.verdict() == Verdict.TOO_SLOW).count();
        if (tooSlow > 0 && slowInLastHalf == tooSlow && tooFast == 0) {
            int drift = (int) Math.round(assessed.stream()
                    .filter(r -> r.verdict() == Verdict.TOO_SLOW)
                    .mapToInt(r -> r.deltaSecPerKm() == null ? 0 : r.deltaSecPerKm())
                    .average().orElse(0));
            return "Les " + tooSlow + " dernières répétitions ont dérivé de " + drift
                    + " s/km : fin de séance en difficulté.";
        }
        long fastInFirstHalf = assessed.subList(0, half).stream()
                .filter(r -> r.verdict() == Verdict.TOO_FAST).count();
        if (tooFast > 0 && fastInFirstHalf == tooFast) {
            return "Départ plus rapide que prescrit sur " + tooFast + " répétition"
                    + (tooFast > 1 ? "s" : "") + ".";
        }
        List<String> parts = new ArrayList<>();
        if (tooFast > 0) {
            parts.add(tooFast + " trop rapide" + (tooFast > 1 ? "s" : ""));
        }
        if (tooSlow > 0) {
            parts.add(tooSlow + " trop lente" + (tooSlow > 1 ? "s" : ""));
        }
        return String.join(", ", parts) + ".";
    }
}
