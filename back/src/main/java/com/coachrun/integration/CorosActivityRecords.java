package com.coachrun.integration;

import com.coachrun.util.FitParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lecture des réponses des outils d'activité de COROS MCP.
 *
 * <p>COROS ne publie pas le format exact de ces réponses : un résumé texte, et parfois un
 * {@code structuredContent} JSON. On lit donc les deux, avec tolérance — d'abord tout objet JSON
 * qui porte un {@code labelId} (contenu structuré, ou texte qui est lui-même du JSON), puis à
 * défaut les blocs numérotés du texte (« 1. … labelId: … »).</p>
 */
public final class CorosActivityRecords {

    private CorosActivityRecords() {
    }

    /** Une sortie listée par {@code querySportRecords}. Seul {@code labelId} est garanti. */
    public record Rec(String labelId, Integer sportType, LocalDate date, Integer distanceM, Integer durationS,
                      String name) {
    }

    private static final Pattern LABEL = Pattern.compile("label_?id\\W{0,6}([A-Za-z0-9_-]{4,64})",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern SPORT = Pattern.compile("sport_?type\\W{0,6}(\\d{1,6})", Pattern.CASE_INSENSITIVE);
    private static final Pattern DATE = Pattern.compile("(\\d{4})-?(\\d{2})-?(\\d{2})");
    private static final Pattern DISTANCE_KM = Pattern.compile("distance\\W{0,4}([\\d.,]+)\\s*km",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern DURATION = Pattern.compile("duration\\W{0,4}(?:(\\d{1,3}):)?(\\d{1,2}):(\\d{2})",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern BLOCK = Pattern.compile("(?m)^\\s*\\d{1,3}\\.\\s");
    private static final Pattern URL = Pattern.compile("https://[^\\s\"'<>()\\[\\]{}]+");

    /** Sorties d'une réponse de {@code querySportRecords}, sans doublon de {@code labelId}. */
    public static List<Rec> records(ObjectMapper mapper, JsonNode result, ZoneId zone) {
        Map<String, Rec> out = new LinkedHashMap<>();
        if (result == null) {
            return List.of();
        }
        collectJson(result.get("structuredContent"), zone, out);
        String text = textOf(result);
        if (out.isEmpty()) {
            JsonNode parsed = tryJson(mapper, text);
            collectJson(parsed, zone, out);
        }
        if (out.isEmpty()) {
            collectText(text, out);
        }
        return new ArrayList<>(out.values());
    }

    /** Liens de téléchargement FIT d'une réponse de {@code queryActivityFitFileDownloadUrls}. */
    public static List<String> fitUrls(ObjectMapper mapper, JsonNode result) {
        Set<String> all = new LinkedHashSet<>();
        if (result == null) {
            return List.of();
        }
        collectUrls(result.get("structuredContent"), all);
        for (JsonNode c : result.path("content")) {
            if (c.hasNonNull("uri") && c.path("uri").asText().startsWith("https://")) {
                all.add(c.path("uri").asText());
            }
            collectUrls(c.get("resource"), all);
        }
        Matcher m = URL.matcher(textOf(result));
        while (m.find()) {
            all.add(trimTrailing(m.group()));
        }
        // Les liens qui nomment un .fit d'abord : un texte peut citer d'autres pages (aide…).
        List<String> fit = new ArrayList<>();
        List<String> other = new ArrayList<>();
        for (String u : all) {
            (u.toLowerCase(java.util.Locale.ROOT).contains(".fit") ? fit : other).add(u);
        }
        return fit.isEmpty() ? other : fit;
    }

    /** Fichiers FIT joints directement à une réponse de {@code downloadActivityFitFiles}. */
    public static List<byte[]> fitBlobs(JsonNode result) {
        List<byte[]> out = new ArrayList<>();
        if (result == null) {
            return out;
        }
        for (JsonNode c : result.path("content")) {
            for (JsonNode candidate : new JsonNode[]{c.path("resource").get("blob"), c.get("blob"), c.get("data")}) {
                if (candidate != null && candidate.isTextual()) {
                    try {
                        byte[] bytes = Base64.getDecoder().decode(candidate.asText().replaceAll("\\s", ""));
                        if (FitParser.looksLikeFit(bytes)) {
                            out.add(bytes);
                        }
                    } catch (IllegalArgumentException notBase64) {
                        // Pas un fichier : on passe.
                    }
                }
            }
        }
        return out;
    }

    /** Texte des blocs {@code content} d'un résultat d'outil MCP. */
    public static String textOf(JsonNode result) {
        StringBuilder sb = new StringBuilder();
        if (result != null) {
            for (JsonNode c : result.path("content")) {
                if (c.hasNonNull("text")) {
                    sb.append(c.path("text").asText()).append('\n');
                }
            }
        }
        return sb.toString();
    }

    // --- JSON -----------------------------------------------------------------------------------

    private static void collectJson(JsonNode node, ZoneId zone, Map<String, Rec> out) {
        if (node == null) {
            return;
        }
        if (node.isObject()) {
            String label = firstText(node, "labelId", "label_id", "activityId");
            if (label != null && !label.isBlank()) {
                out.putIfAbsent(label, new Rec(label,
                        firstInt(node, "sportType", "sport_type", "mode"),
                        dateOf(node, zone),
                        distanceOf(node),
                        durationOf(node),
                        firstText(node, "name", "title")));
                return;
            }
            Iterator<JsonNode> it = node.elements();
            while (it.hasNext()) {
                collectJson(it.next(), zone, out);
            }
        } else if (node.isArray()) {
            for (JsonNode child : node) {
                collectJson(child, zone, out);
            }
        }
    }

    private static LocalDate dateOf(JsonNode node, ZoneId zone) {
        for (String f : new String[]{"date", "startDate", "happenDay"}) {
            JsonNode v = node.get(f);
            if (v != null && !v.isNull()) {
                Matcher m = DATE.matcher(v.asText());
                if (m.find()) {
                    return safeDate(m);
                }
            }
        }
        for (String f : new String[]{"startTime", "startTimestamp"}) {
            JsonNode v = node.get(f);
            if (v != null && v.isNumber()) {
                long t = v.asLong();
                // Secondes ou millisecondes : au-delà de l'an 2100 en secondes, ce sont des ms.
                Instant at = t > 4_102_444_800L ? Instant.ofEpochMilli(t) : Instant.ofEpochSecond(t);
                return at.atZone(zone).toLocalDate();
            }
            if (v != null && v.isTextual()) {
                Matcher m = DATE.matcher(v.asText());
                if (m.find()) {
                    return safeDate(m);
                }
            }
        }
        return null;
    }

    private static Integer distanceOf(JsonNode node) {
        // Seule une distance en km déclarée comme telle est lue : COROS exprime ailleurs les
        // distances en centimètres (tours : 100000 pour 1 km), et l'on ne devine pas l'unité.
        JsonNode km = node.get("distanceKm");
        return km != null && km.isNumber() ? (int) Math.round(km.asDouble() * 1000) : null;
    }

    private static Integer durationOf(JsonNode node) {
        for (String f : new String[]{"durationSeconds", "totalTime", "duration"}) {
            JsonNode v = node.get(f);
            if (v != null && v.isNumber() && v.asDouble() > 0) {
                return (int) Math.round(v.asDouble());
            }
        }
        return null;
    }

    private static String firstText(JsonNode node, String... fields) {
        for (String f : fields) {
            JsonNode v = node.get(f);
            if (v != null && (v.isTextual() || v.isNumber()) && !v.asText().isBlank()) {
                return v.asText();
            }
        }
        return null;
    }

    private static Integer firstInt(JsonNode node, String... fields) {
        for (String f : fields) {
            JsonNode v = node.get(f);
            if (v != null && v.canConvertToInt()) {
                return v.asInt();
            }
            if (v != null && v.isTextual() && v.asText().matches("\\d{1,6}")) {
                return Integer.valueOf(v.asText());
            }
        }
        return null;
    }

    private static JsonNode tryJson(ObjectMapper mapper, String text) {
        String t = text == null ? "" : text.trim();
        if (!(t.startsWith("{") || t.startsWith("["))) {
            return null;
        }
        try {
            return mapper.readTree(t);
        } catch (Exception notJson) {
            return null;
        }
    }

    private static void collectUrls(JsonNode node, Set<String> out) {
        if (node == null) {
            return;
        }
        if (node.isTextual()) {
            Matcher m = URL.matcher(node.asText());
            while (m.find()) {
                out.add(trimTrailing(m.group()));
            }
        } else if (node.isContainerNode()) {
            for (JsonNode child : node) {
                collectUrls(child, out);
            }
        }
    }

    // --- Texte ----------------------------------------------------------------------------------

    private static void collectText(String text, Map<String, Rec> out) {
        if (text == null || text.isBlank()) {
            return;
        }
        Matcher b = BLOCK.matcher(text);
        List<Integer> starts = new ArrayList<>();
        while (b.find()) {
            starts.add(b.start());
        }
        if (starts.isEmpty()) {
            starts.add(0);
        }
        for (int i = 0; i < starts.size(); i++) {
            String block = text.substring(starts.get(i), i + 1 < starts.size() ? starts.get(i + 1) : text.length());
            Matcher l = LABEL.matcher(block);
            if (!l.find()) {
                continue;
            }
            String label = l.group(1);
            Matcher s = SPORT.matcher(block);
            Integer sport = s.find() ? Integer.valueOf(s.group(1)) : null;
            Matcher d = DATE.matcher(block.replace(label, ""));
            LocalDate date = null;
            while (date == null && d.find()) {
                date = safeDate(d);
            }
            Matcher km = DISTANCE_KM.matcher(block);
            Integer distance = km.find() ? parseKm(km.group(1)) : null;
            Matcher du = DURATION.matcher(block);
            Integer duration = null;
            if (du.find()) {
                int h = du.group(1) == null ? 0 : Integer.parseInt(du.group(1));
                duration = h * 3600 + Integer.parseInt(du.group(2)) * 60 + Integer.parseInt(du.group(3));
            }
            out.putIfAbsent(label, new Rec(label, sport, date, distance, duration, null));
        }
    }

    private static Integer parseKm(String s) {
        try {
            return (int) Math.round(Double.parseDouble(s.replace(',', '.')) * 1000);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static LocalDate safeDate(Matcher m) {
        try {
            int y = Integer.parseInt(m.group(1));
            if (y < 2000 || y > 2100) {
                return null;
            }
            return LocalDate.of(y, Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3)));
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private static String trimTrailing(String url) {
        return url.replaceAll("[.,;:!?]+$", "");
    }

    /** {@code yyyyMMdd}, le format de date des outils COROS. */
    public static final DateTimeFormatter COROS_DATE = DateTimeFormatter.BASIC_ISO_DATE;
}
