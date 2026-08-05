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
 * Décodeur d'activités <b>GPX / TCX</b> (XML, sans dépendance externe).
 *
 * <p>Il ne fait que lire le fichier : la géométrie et le flux temps-en-zone sont calculés par
 * {@link ActivityTrack}, partagé avec le décodeur FIT. Le binaire des montres, lui, est
 * l'affaire de {@link FitParser} ; {@link ActivityFileParser} choisit entre les deux.</p>
 */
public final class GpxParser {

    private GpxParser() {
    }

    public static ActivityTrack.ParsedActivity parse(byte[] content) {
        List<ActivityTrack.Point> points = readPoints(content);
        if (points.isEmpty()) {
            throw new IllegalArgumentException("Fichier sans points GPS exploitables.");
        }

        Instant first = points.get(0).time();
        Instant last = points.get(points.size() - 1).time();
        Integer durationS = (first != null && last != null)
                ? (int) Duration.between(first, last).getSeconds() : null;
        LocalDate date = first != null ? first.atZone(ZoneOffset.UTC).toLocalDate() : LocalDate.now();

        List<ActivityTrack.Point> positioned = ActivityTrack.positioned(points);
        return new ActivityTrack.ParsedActivity(date,
                ActivityTrack.distanceM(positioned), durationS,
                ActivityTrack.elevationGainM(points), ActivityTrack.avgHr(points),
                ActivityTrack.downsample(positioned), ActivityTrack.buildStream(points),
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

    private static List<ActivityTrack.Point> readPoints(byte[] content) {
        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            dbf.setNamespaceAware(false);
            var doc = dbf.newDocumentBuilder().parse(new ByteArrayInputStream(content));

            List<ActivityTrack.Point> points = new ArrayList<>();
            // GPX : <trkpt lat lon><ele/><time/>
            NodeList trkpts = doc.getElementsByTagName("trkpt");
            for (int i = 0; i < trkpts.getLength(); i++) {
                Element e = (Element) trkpts.item(i);
                points.add(ActivityTrack.Point.gps(
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
                points.add(ActivityTrack.Point.gps(lat, lon, childDouble(e, "AltitudeMeters"),
                        childInstant(e, "Time"), tcxHr(e)));
            }
            return points;
        } catch (Exception e) {
            throw new IllegalArgumentException("Fichier GPX/TCX invalide.", e);
        }
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
