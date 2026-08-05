package com.coachrun.util;

import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

/**
 * Parseur d'activités GPX / TCX (XML, sans dépendance externe). Calcule distance (haversine),
 * durée, dénivelé positif et renvoie un tracé sous-échantillonné. FIT (binaire) non géré ici.
 */
public final class GpxParser {

    private static final int MAX_POINTS = 400;
    private static final double EARTH_RADIUS_M = 6_371_000;

    private GpxParser() {
    }

    public record Point(double lat, double lon, Double ele, Instant time, Integer hr) {
    }

    /**
     * Échantillon de flux (temps-en-zone) : secondes écoulées depuis le départ, FC instantanée et
     * allure instantanée (s/km). {@code -1} = valeur absente. Cf. PROPOSITION-ZONES §3.7 / E7 (V2-7).
     */
    public record Sample(int elapsedS, int hr, int paceSecPerKm) {
    }

    /**
     * @param laps tours relevés par la montre (TCX uniquement — le GPX n'en porte pas). Vide
     *             quand le fichier n'en déclare qu'un : une sortie continue n'a pas de tours,
     *             l'écran retombera alors sur des splits kilométriques calculés.
     */
    public record ParsedActivity(
            LocalDate date, Integer distanceM, Integer durationS, Integer elevationGainM,
            List<double[]> route, List<int[]> stream,
            List<com.coachrun.dto.response.ActivityLapsResponse.Lap> laps) {
    }

    public static ParsedActivity parse(byte[] content) {
        List<Point> points = readPoints(content);
        if (points.isEmpty()) {
            throw new IllegalArgumentException("Fichier sans points GPS exploitables.");
        }

        double distance = 0;
        double elevationGain = 0;
        for (int i = 1; i < points.size(); i++) {
            distance += haversine(points.get(i - 1), points.get(i));
            Double prev = points.get(i - 1).ele();
            Double cur = points.get(i).ele();
            if (prev != null && cur != null && cur > prev) {
                elevationGain += cur - prev;
            }
        }

        Instant first = points.get(0).time();
        Instant last = points.get(points.size() - 1).time();
        Integer durationS = (first != null && last != null)
                ? (int) Duration.between(first, last).getSeconds() : null;
        LocalDate date = first != null ? first.atZone(ZoneOffset.UTC).toLocalDate() : LocalDate.now();

        return new ParsedActivity(date, (int) Math.round(distance), durationS,
                (int) Math.round(elevationGain), downsample(points), buildStream(points),
                readTcxLaps(content));
    }

    /**
     * Tours d'un fichier TCX ({@code <Lap>}), dans l'ordre du fichier.
     *
     * <p>C'est là que vit le fractionné : un TCX de montre découpe « 10 × 400 / 200 » en vingt
     * et un tours, avec leur temps, leur distance et leur FC. Le GPX, lui, n'a pas de notion de
     * tour — d'où le repli sur des splits kilométriques côté lecture.</p>
     *
     * <p>Un tour unique n'en est pas un : la montre a simplement enregistré la sortie d'un bloc.
     * On renvoie alors une liste vide plutôt qu'un « tour 1 » qui répéterait le total.</p>
     */
    private static List<com.coachrun.dto.response.ActivityLapsResponse.Lap> readTcxLaps(byte[] content) {
        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            dbf.setNamespaceAware(false);
            var doc = dbf.newDocumentBuilder().parse(new ByteArrayInputStream(content));

            NodeList nodes = doc.getElementsByTagName("Lap");
            List<com.coachrun.dto.response.ActivityLapsResponse.Lap> laps = new ArrayList<>();
            for (int i = 0; i < nodes.getLength(); i++) {
                Element lap = (Element) nodes.item(i);
                Double seconds = directChildDouble(lap, "TotalTimeSeconds");
                Double meters = directChildDouble(lap, "DistanceMeters");
                if (seconds == null && meters == null) {
                    continue;
                }
                laps.add(com.coachrun.dto.response.ActivityLapsResponse.Lap.of(
                        laps.size() + 1,
                        meters != null ? (int) Math.round(meters) : null,
                        seconds != null ? (int) Math.round(seconds) : null,
                        nestedValue(lap, "AverageHeartRateBpm"),
                        nestedValue(lap, "MaximumHeartRateBpm"),
                        intOrNull(directChildDouble(lap, "Cadence")),
                        null));
            }
            return laps.size() > 1 ? laps : List.of();
        } catch (Exception e) {
            return List.of(); // les tours sont un bonus : leur absence n'invalide pas le fichier
        }
    }

    /**
     * Valeur d'un enfant DIRECT, jamais d'un descendant : un {@code <Lap>} contient ses
     * {@code <Trackpoint>}, dont les {@code <DistanceMeters>} donneraient la distance cumulée
     * du dernier point au lieu de celle du tour.
     */
    private static Double directChildDouble(Element parent, String tag) {
        for (Node n = parent.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n.getNodeType() == Node.ELEMENT_NODE && localName(n).equals(tag)) {
                String t = n.getTextContent();
                if (t != null && !t.isBlank()) {
                    try {
                        return Double.parseDouble(t.trim());
                    } catch (NumberFormatException ignored) {
                        return null;
                    }
                }
            }
        }
        return null;
    }

    /** {@code <AverageHeartRateBpm><Value>152</Value></AverageHeartRateBpm>} en enfant direct. */
    private static Integer nestedValue(Element parent, String tag) {
        for (Node n = parent.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n.getNodeType() == Node.ELEMENT_NODE && localName(n).equals(tag)) {
                Element e = (Element) n;
                NodeList v = e.getElementsByTagName("Value");
                return parseIntOrNull(v.getLength() > 0 ? v.item(0).getTextContent() : e.getTextContent());
            }
        }
        return null;
    }

    private static String localName(Node n) {
        String name = n.getNodeName();
        int c = name.indexOf(':');
        return c >= 0 ? name.substring(c + 1) : name;
    }

    private static Integer intOrNull(Double d) {
        return d != null && d > 0 ? (int) Math.round(d) : null;
    }

    /**
     * Flux échantillonné [elapsedS, hr, paceSecPerKm] (-1 = absent) pour le calcul du temps-en-zone.
     * L'allure instantanée est lissée sur une fenêtre (~15 s / plusieurs points) pour amortir le
     * bruit GPS. Vide si aucun horodatage exploitable.
     */
    private static List<int[]> buildStream(List<Point> points) {
        Instant start = points.get(0).time();
        if (start == null) {
            return List.of();
        }
        int step = Math.max(1, points.size() / MAX_POINTS);
        List<int[]> stream = new ArrayList<>();
        for (int i = 0; i < points.size(); i += step) {
            Point p = points.get(i);
            if (p.time() == null) {
                continue;
            }
            int elapsed = (int) Duration.between(start, p.time()).getSeconds();
            int hr = p.hr() != null ? p.hr() : -1;
            int pace = instantPace(points, i, step);
            stream.add(new int[] {elapsed, hr, pace});
        }
        return stream;
    }

    /** Allure instantanée (s/km) autour du point i, moyennée sur la fenêtre [i, i+step] ; -1 si N/A. */
    private static int instantPace(List<Point> points, int i, int step) {
        int j = Math.min(points.size() - 1, i + step);
        if (j <= i) {
            return -1;
        }
        Point a = points.get(i);
        Point b = points.get(j);
        if (a.time() == null || b.time() == null) {
            return -1;
        }
        double dtS = Duration.between(a.time(), b.time()).toMillis() / 1000.0;
        double dm = 0;
        for (int k = i + 1; k <= j; k++) {
            dm += haversine(points.get(k - 1), points.get(k));
        }
        if (dtS <= 0 || dm < 1) {
            return -1; // arrêt / GPS immobile → allure non définie
        }
        double secPerKm = dtS / (dm / 1000.0);
        if (secPerKm < 120 || secPerKm > 1800) {
            return -1; // hors plage plausible (2:00 → 30:00 /km)
        }
        return (int) Math.round(secPerKm);
    }

    private static List<Point> readPoints(byte[] content) {
        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            dbf.setNamespaceAware(false);
            var doc = dbf.newDocumentBuilder().parse(new ByteArrayInputStream(content));

            List<Point> points = new ArrayList<>();
            // GPX : <trkpt lat lon><ele/><time/>
            NodeList trkpts = doc.getElementsByTagName("trkpt");
            for (int i = 0; i < trkpts.getLength(); i++) {
                Element e = (Element) trkpts.item(i);
                points.add(new Point(
                        parseD(e.getAttribute("lat")), parseD(e.getAttribute("lon")),
                        childDouble(e, "ele"), childInstant(e, "time"), gpxHr(e)));
            }
            if (!points.isEmpty()) {
                return points;
            }
            // TCX : <Trackpoint><Position><LatitudeDegrees/><LongitudeDegrees/></Position>...
            NodeList tps = doc.getElementsByTagName("Trackpoint");
            for (int i = 0; i < tps.getLength(); i++) {
                Element e = (Element) tps.item(i);
                Double lat = childDouble(e, "LatitudeDegrees");
                Double lon = childDouble(e, "LongitudeDegrees");
                if (lat == null || lon == null) {
                    continue;
                }
                points.add(new Point(lat, lon, childDouble(e, "AltitudeMeters"),
                        childInstant(e, "Time"), tcxHr(e)));
            }
            return points;
        } catch (Exception e) {
            throw new IllegalArgumentException("Fichier GPX/TCX invalide.", e);
        }
    }

    private static double haversine(Point a, Point b) {
        double dLat = Math.toRadians(b.lat() - a.lat());
        double dLon = Math.toRadians(b.lon() - a.lon());
        double h = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(a.lat())) * Math.cos(Math.toRadians(b.lat()))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return EARTH_RADIUS_M * 2 * Math.atan2(Math.sqrt(h), Math.sqrt(1 - h));
    }

    private static List<double[]> downsample(List<Point> points) {
        int step = Math.max(1, points.size() / MAX_POINTS);
        List<double[]> route = new ArrayList<>();
        for (int i = 0; i < points.size(); i += step) {
            route.add(new double[] {
                    Math.round(points.get(i).lat() * 1e5) / 1e5,
                    Math.round(points.get(i).lon() * 1e5) / 1e5 });
        }
        return route;
    }

    private static Double childDouble(Element parent, String tag) {
        String v = childText(parent, tag);
        return v == null ? null : parseD(v);
    }

    private static Instant childInstant(Element parent, String tag) {
        String v = childText(parent, tag);
        try {
            return v == null ? null : Instant.parse(v);
        } catch (Exception e) {
            return null;
        }
    }

    private static String childText(Element parent, String tag) {
        NodeList nl = parent.getElementsByTagName(tag);
        for (int i = 0; i < nl.getLength(); i++) {
            Node n = nl.item(i);
            if (n.getParentNode() == parent || tag.equals("ele") || tag.equals("time")
                    || tag.equals("AltitudeMeters") || tag.equals("Time")
                    || tag.equals("LatitudeDegrees") || tag.equals("LongitudeDegrees")) {
                String t = n.getTextContent();
                if (t != null && !t.isBlank()) {
                    return t.trim();
                }
            }
        }
        return null;
    }

    private static double parseD(String s) {
        return Double.parseDouble(s.trim());
    }

    /** FC d'un {@code <trkpt>} GPX : dans {@code <extensions>…<gpxtpx:hr>} (préfixe de ns variable). */
    private static Integer gpxHr(Element trkpt) {
        NodeList all = trkpt.getElementsByTagName("*");
        for (int i = 0; i < all.getLength(); i++) {
            String name = all.item(i).getNodeName();
            int c = name.indexOf(':');
            String local = c >= 0 ? name.substring(c + 1) : name;
            if (local.equalsIgnoreCase("hr")) {
                return parseIntOrNull(all.item(i).getTextContent());
            }
        }
        return null;
    }

    /** FC d'un {@code <Trackpoint>} TCX : {@code <HeartRateBpm><Value>142</Value></HeartRateBpm>}. */
    private static Integer tcxHr(Element tp) {
        NodeList hb = tp.getElementsByTagName("HeartRateBpm");
        if (hb.getLength() == 0) {
            return null;
        }
        Element h = (Element) hb.item(0);
        NodeList v = h.getElementsByTagName("Value");
        return parseIntOrNull(v.getLength() > 0 ? v.item(0).getTextContent() : h.getTextContent());
    }

    private static Integer parseIntOrNull(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        try {
            int v = (int) Math.round(Double.parseDouble(s.trim()));
            return v > 0 ? v : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
