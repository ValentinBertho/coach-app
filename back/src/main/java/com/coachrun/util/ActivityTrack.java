package com.coachrun.util;

import com.coachrun.dto.response.ActivityLapsResponse;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Ce qu'une trace importée a de commun, quel que soit son format : les points relevés, et les
 * calculs qui en découlent (distance, dénivelé, tracé sous-échantillonné, flux temps-en-zone).
 *
 * <p>Deux décodeurs alimentent ce modèle — {@link GpxParser} pour le XML (GPX/TCX) et
 * {@link FitParser} pour le binaire des montres. Ils n'ont en commun ni leur lecture ni leurs
 * pièges, mais <b>exactement</b> la même arithmétique en sortie : elle vit ici pour qu'un tracé
 * importé en FIT et le même tracé exporté en GPX ne donnent jamais deux distances différentes.</p>
 */
public final class ActivityTrack {

    /** Plafond de points renvoyés au client : au-delà, la carte et le graphe ne gagnent rien. */
    private static final int MAX_POINTS = 400;
    private static final double EARTH_RADIUS_M = 6_371_000;

    private ActivityTrack() {
    }

    /**
     * Un point relevé. Tout est optionnel : une montre en salle n'a pas de position, un GPX sans
     * capteur n'a pas de FC, et un vieux fichier n'a pas de vitesse instantanée.
     *
     * @param speedMs vitesse donnée par l'appareil (m/s). Quand elle existe, elle prime sur la
     *                dérivation GPS pour l'allure du flux : c'est la mesure de la montre, pas une
     *                différence de deux positions bruitées.
     */
    public record Point(Double lat, Double lon, Double ele, Instant time, Integer hr, Double speedMs) {

        /** Point issu d'une trace GPS, sans vitesse mesurée. */
        public static Point gps(double lat, double lon, Double ele, Instant time, Integer hr) {
            return new Point(lat, lon, ele, time, hr, null);
        }

        public boolean positioned() {
            return lat != null && lon != null;
        }
    }

    /**
     * Résultat d'un décodage, prêt à devenir une {@code Activity}.
     *
     * @param avgHr FC moyenne de la sortie, ou {@code null} si aucun capteur n'a parlé
     * @param laps  tours relevés par la montre. Vide quand le fichier n'en déclare qu'un : une
     *              sortie continue n'a pas de tours, l'écran retombera alors sur des splits
     *              kilométriques calculés.
     */
    public record ParsedActivity(
            LocalDate date, Integer distanceM, Integer durationS, Integer elevationGainM,
            Integer avgHr, List<double[]> route, List<int[]> stream,
            List<ActivityLapsResponse.Lap> laps) {
    }

    /** Les points qui portent une position, seuls exploitables pour la géométrie. */
    public static List<Point> positioned(List<Point> points) {
        return points.stream().filter(Point::positioned).toList();
    }

    /** Distance cumulée (m) le long de la trace, par haversine. */
    public static int distanceM(List<Point> positioned) {
        double d = 0;
        for (int i = 1; i < positioned.size(); i++) {
            d += haversine(positioned.get(i - 1), positioned.get(i));
        }
        return (int) Math.round(d);
    }

    /** Dénivelé positif (m) : somme des montées entre points consécutifs qui portent une altitude. */
    public static int elevationGainM(List<Point> points) {
        double gain = 0;
        Double previous = null;
        for (Point p : points) {
            if (p.ele() == null) {
                continue;
            }
            if (previous != null && p.ele() > previous) {
                gain += p.ele() - previous;
            }
            previous = p.ele();
        }
        return (int) Math.round(gain);
    }

    /** FC moyenne des points qui en portent une, ou {@code null} si aucun. */
    public static Integer avgHr(List<Point> points) {
        long sum = 0;
        int n = 0;
        for (Point p : points) {
            if (p.hr() != null && p.hr() > 0) {
                sum += p.hr();
                n++;
            }
        }
        return n == 0 ? null : (int) Math.round(sum / (double) n);
    }

    /** Tracé sous-échantillonné à {@value #MAX_POINTS} points, arrondi à 5 décimales (~1 m). */
    public static List<double[]> downsample(List<Point> positioned) {
        int step = Math.max(1, positioned.size() / MAX_POINTS);
        List<double[]> route = new ArrayList<>();
        for (int i = 0; i < positioned.size(); i += step) {
            route.add(new double[] {
                    Math.round(positioned.get(i).lat() * 1e5) / 1e5,
                    Math.round(positioned.get(i).lon() * 1e5) / 1e5 });
        }
        return route;
    }

    /**
     * Flux échantillonné {@code [elapsedS, hr, paceSecPerKm]} ({@code -1} = absent) pour le calcul
     * du temps-en-zone. L'allure vient de la vitesse mesurée quand la montre la donne, sinon d'une
     * dérivation GPS lissée sur une fenêtre (~15 s / plusieurs points) qui amortit le bruit.
     * Vide si aucun horodatage exploitable.
     */
    public static List<int[]> buildStream(List<Point> points) {
        if (points.isEmpty() || points.get(0).time() == null) {
            return List.of();
        }
        Instant start = points.get(0).time();
        int step = Math.max(1, points.size() / MAX_POINTS);
        List<int[]> stream = new ArrayList<>();
        for (int i = 0; i < points.size(); i += step) {
            Point p = points.get(i);
            if (p.time() == null) {
                continue;
            }
            int elapsed = (int) Duration.between(start, p.time()).getSeconds();
            int hr = p.hr() != null ? p.hr() : -1;
            stream.add(new int[] {elapsed, hr, instantPace(points, i, step)});
        }
        return stream;
    }

    /** Allure instantanée (s/km) autour du point i ; {@code -1} si indéterminable ou implausible. */
    private static int instantPace(List<Point> points, int i, int step) {
        Point a = points.get(i);
        if (a.speedMs() != null) {
            // La montre a mesuré sa vitesse : la dériver de deux positions serait moins juste.
            return a.speedMs() > 0 ? plausible(1000.0 / a.speedMs()) : -1;
        }
        int j = Math.min(points.size() - 1, i + step);
        if (j <= i) {
            return -1;
        }
        Point b = points.get(j);
        if (a.time() == null || b.time() == null || !a.positioned() || !b.positioned()) {
            return -1;
        }
        double dtS = Duration.between(a.time(), b.time()).toMillis() / 1000.0;
        double dm = 0;
        for (int k = i + 1; k <= j; k++) {
            if (points.get(k - 1).positioned() && points.get(k).positioned()) {
                dm += haversine(points.get(k - 1), points.get(k));
            }
        }
        if (dtS <= 0 || dm < 1) {
            return -1; // arrêt / GPS immobile → allure non définie
        }
        return plausible(dtS / (dm / 1000.0));
    }

    /** Filtre de vraisemblance : hors de 2:00 → 30:00 /km, on préfère « inconnu » à un chiffre faux. */
    private static int plausible(double secPerKm) {
        return (secPerKm < 120 || secPerKm > 1800) ? -1 : (int) Math.round(secPerKm);
    }

    static double haversine(Point a, Point b) {
        double dLat = Math.toRadians(b.lat() - a.lat());
        double dLon = Math.toRadians(b.lon() - a.lon());
        double h = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(a.lat())) * Math.cos(Math.toRadians(b.lat()))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return EARTH_RADIUS_M * 2 * Math.atan2(Math.sqrt(h), Math.sqrt(1 - h));
    }
}
