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

    public record Point(double lat, double lon, Double ele, Instant time) {
    }

    public record ParsedActivity(
            LocalDate date, Integer distanceM, Integer durationS, Integer elevationGainM,
            List<double[]> route) {
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
                (int) Math.round(elevationGain), downsample(points));
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
                        childDouble(e, "ele"), childInstant(e, "time")));
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
                points.add(new Point(lat, lon, childDouble(e, "AltitudeMeters"), childInstant(e, "Time")));
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
                    || tag.equals("AltitudeMeters") || tag.equals("Time")) {
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
}
