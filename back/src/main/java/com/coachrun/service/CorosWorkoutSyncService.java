package com.coachrun.service;

import com.coachrun.dto.response.CalculatedSessionResponse;
import com.coachrun.entity.CorosWorkoutSync;
import com.coachrun.entity.DeviceConnection;
import com.coachrun.entity.Workout;
import com.coachrun.entity.enums.DeviceProvider;
import com.coachrun.entity.enums.WorkoutStatus;
import com.coachrun.entity.enums.WorkoutType;
import com.coachrun.integration.CorosMcpClient;
import com.coachrun.integration.CorosMcpClient.Endpoints;
import com.coachrun.integration.CorosWorkoutMapper;
import com.coachrun.integration.CorosWorkoutMapper.Vocabulary;
import com.coachrun.integration.GarminWorkoutMapper;
import com.coachrun.repository.CorosWorkoutSyncRepository;
import com.coachrun.repository.DeviceConnectionRepository;
import com.coachrun.repository.WorkoutRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientResponseException;

/**
 * Envoi des séances course prescrites dans le calendrier COROS de l'athlète (via COROS MCP).
 *
 * <h2>Une réconciliation, comme pour Garmin</h2>
 *
 * <p>On compare ce qui <b>devrait</b> être chez COROS (les séances course des
 * {@link #WINDOW_DAYS} prochains jours) à ce qui <b>y est</b> ({@link CorosWorkoutSync}), et on
 * corrige l'écart. Aucun appel tant qu'une séance ne change pas (empreinte du contenu).</p>
 *
 * <h2>Ce que COROS ne permet pas</h2>
 *
 * <p>COROS MCP sait créer une séance à une date ({@code createScheduledWorkout}) et la réécrire
 * ({@code updateScheduledWorkout}), mais <b>ni la supprimer ni la déplacer</b>. Une séance retirée
 * ou déplacée par le coach est donc réécrite en « Annulée par ton coach » à son ancienne date, et
 * la séance déplacée est créée à sa nouvelle date. L'athlète peut effacer l'annulée dans l'app
 * COROS.</p>
 *
 * <h2>Un format à moitié documenté</h2>
 *
 * <p>Le schéma de l'objet {@code course} ne se lit que dans {@code tools/list} : il est lu une
 * fois par serveur régional et par {@link #VOCABULARY_TTL} pour compléter les codes connus
 * ({@link Vocabulary#fromSchema}). Si COROS refuse la forme structurée, la séance est renvoyée
 * sous sa forme simple (une section, toute la prescription en description) plutôt que pas du
 * tout.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CorosWorkoutSyncService {

    /** Horizon envoyé : aujourd'hui et les 13 jours suivants (comme Garmin). */
    public static final int WINDOW_DAYS = 14;
    static final String CREATE = "createScheduledWorkout";
    static final String UPDATE = "updateScheduledWorkout";
    private static final Duration VOCABULARY_TTL = Duration.ofHours(6);
    private static final DateTimeFormatter COROS_DATE = DateTimeFormatter.BASIC_ISO_DATE;
    private static final Set<WorkoutType> NOT_RUNNING =
            Set.of(WorkoutType.STRENGTH, WorkoutType.CROSS_TRAINING, WorkoutType.REST);
    private static final Pattern ID_IN_PLAN = Pattern.compile("idInPlan\\W{0,6}(\\d{1,30})");

    private final CorosMcpClient client;
    private final CorosService corosService;
    private final DeviceConnectionRepository connectionRepository;
    private final CorosWorkoutSyncRepository syncRepository;
    private final WorkoutRepository workoutRepository;
    private final ClockService clock;
    private final ObjectMapper objectMapper;

    private final Set<UUID> inFlight = ConcurrentHashMap.newKeySet();
    private final Map<String, CachedVocabulary> vocabularies = new ConcurrentHashMap<>();

    private record CachedVocabulary(Vocabulary vocabulary, Instant at) {
    }

    /** Bilan d'une synchronisation, pour l'écran et les journaux. */
    public record Result(int created, int updated, int cancelled, int failed, boolean skipped) {
        static Result skippedResult() {
            return new Result(0, 0, 0, 0, true);
        }
    }

    public record Summary(long workoutsOnCoros, long workoutsInError) {
    }

    /** Une séance course éligible à l'envoi ? (COROS MCP ne crée pas de renforcement.) */
    public static boolean eligible(Workout w, LocalDate today) {
        return w.getStatus() == WorkoutStatus.PLANNED
                && w.getType() != null && !NOT_RUNNING.contains(w.getType())
                && w.getScheduledDate() != null
                && !w.getScheduledDate().isBefore(today)
                && w.getScheduledDate().isBefore(today.plusDays(WINDOW_DAYS));
    }

    @Transactional
    public Result syncAthlete(UUID athleteId) {
        if (!client.isConfigured() || !inFlight.add(athleteId)) {
            return Result.skippedResult();
        }
        try {
            DeviceConnection conn = connectionRepository
                    .findByAthleteIdAndProvider(athleteId, DeviceProvider.COROS).orElse(null);
            if (conn == null) {
                return Result.skippedResult();
            }
            return reconcile(athleteId, conn);
        } finally {
            inFlight.remove(athleteId);
        }
    }

    @Transactional(readOnly = true)
    public Summary summary(UUID athleteId) {
        LocalDate today = clock.today();
        return new Summary(syncRepository.countOnCoros(athleteId, today),
                syncRepository.countInError(athleteId, today));
    }

    /** À la déconnexion : on oublie le suivi. COROS ne permet pas de retirer ce qui y est. */
    @Transactional
    public void forgetAthlete(UUID athleteId) {
        syncRepository.deleteByAthleteId(athleteId);
    }

    private Result reconcile(UUID athleteId, DeviceConnection conn) {
        LocalDate today = clock.today();
        String token;
        Endpoints e;
        try {
            token = corosService.freshAccessToken(conn);
            e = corosService.endpointsOf(conn);
        } catch (RuntimeException ex) {
            log.warn("COROS : jeton indisponible pour l'athlète {} : {}", athleteId, ex.getMessage());
            return new Result(0, 0, 0, 1, false);
        }

        Map<String, Workout> wanted = new LinkedHashMap<>();
        for (Workout w : workoutRepository.findByAthleteIdAndScheduledDateBetweenOrderByScheduledDateAsc(
                athleteId, today, today.plusDays(WINDOW_DAYS - 1))) {
            if (eligible(w, today)) {
                wanted.put(key(w.getId(), w.getScheduledDate()), w);
            }
        }

        int created = 0;
        int updated = 0;
        int cancelled = 0;
        int failed = 0;
        Vocabulary vocabulary = null;

        // 1. Ce qui est chez COROS : gardé, oublié (passé) ou à annuler (retiré, déplacé).
        Map<String, CorosWorkoutSync> placed = new HashMap<>();
        List<CorosWorkoutSync> reusable = new ArrayList<>();
        for (CorosWorkoutSync row : syncRepository.findByAthleteId(athleteId)) {
            if (row.getScheduledDate().isBefore(today)) {
                syncRepository.delete(row); // passée : elle reste dans l'historique COROS
                continue;
            }
            String k = row.getWorkoutId() != null ? key(row.getWorkoutId(), row.getScheduledDate()) : null;
            if (k != null && wanted.containsKey(k) && !placed.containsKey(k)) {
                placed.put(k, row);
                continue;
            }
            if (row.isCancelled()) {
                reusable.add(row);
                continue;
            }
            if (row.getIdInPlan() == null) {
                syncRepository.delete(row); // jamais arrivée chez COROS : rien à annuler
                continue;
            }
            try {
                vocabulary = vocabulary != null ? vocabulary : vocabulary(e, token);
                Map<String, Object> course = CorosWorkoutMapper.cancelledCourse(row.getTitle(), vocabulary);
                String newId = update(e, token, row, course);
                row.setIdInPlan(newId != null ? newId : row.getIdInPlan());
                row.setCancelled(true);
                row.setWorkoutId(null);
                row.setContentHash(null);
                row.setLastError(null);
                syncRepository.save(row);
                cancelled++;
            } catch (Unauthorized ex) {
                markError(row, ex.getMessage());
                return new Result(created, updated, cancelled, failed + 1, false);
            } catch (RuntimeException ex) {
                failed++;
                markError(row, "Annulation impossible : " + ex.getMessage());
            }
        }

        // 2. Ce qui doit y être : créé, réécrit si le contenu a changé.
        for (Map.Entry<String, Workout> entry : wanted.entrySet()) {
            Workout w = entry.getValue();
            CorosWorkoutSync row = placed.get(entry.getKey());
            if (row == null) {
                // Une annulée à la même date peut resservir : mieux vaut la réécrire qu'en ajouter une.
                row = takeReusable(reusable, w.getScheduledDate());
            }
            try {
                vocabulary = vocabulary != null ? vocabulary : vocabulary(e, token);
            } catch (Unauthorized ex) {
                return new Result(created, updated, cancelled, failed + 1, false);
            }
            GarminWorkoutMapper.Source source = sourceOf(w);
            Map<String, Object> course;
            try {
                course = CorosWorkoutMapper.toCourse(source, vocabulary);
            } catch (IllegalArgumentException ex) {
                try {
                    course = CorosWorkoutMapper.toSimpleCourse(source, vocabulary);
                } catch (IllegalArgumentException ex2) {
                    row = row != null ? row : newRow(athleteId, w);
                    failed++;
                    markError(row, ex2.getMessage());
                    continue;
                }
            }
            String hash = hash(course, w.getScheduledDate());
            if (row != null && !row.isCancelled() && row.getIdInPlan() != null && hash.equals(row.getContentHash())
                    && row.getLastError() == null) {
                continue; // inchangée : aucun appel
            }
            boolean rewriting = row != null && row.getIdInPlan() != null;
            if (row == null) {
                row = newRow(athleteId, w);
            }
            row.setWorkoutId(w.getId());
            row.setTitle(truncate(w.getTitle(), 120));
            try {
                String id = send(e, token, row, rewriting, course, source, vocabulary);
                if (id != null) {
                    row.setIdInPlan(id);
                } else if (!rewriting) {
                    log.warn("COROS n'a pas renvoyé d'idInPlan pour la séance {} : elle ne pourra pas être réécrite",
                            w.getId());
                }
                row.setCancelled(false);
                row.setContentHash(hash);
                row.setLastError(null);
                syncRepository.save(row);
                if (rewriting) {
                    updated++;
                } else {
                    created++;
                }
            } catch (Unauthorized ex) {
                failed++;
                markError(row, ex.getMessage());
                return new Result(created, updated, cancelled, failed, false);
            } catch (RuntimeException ex) {
                failed++;
                markError(row, "Envoi impossible : " + ex.getMessage());
            }
        }

        if (created + updated + cancelled + failed > 0) {
            log.info("COROS athlète {} : {} créée(s), {} réécrite(s), {} annulée(s), {} échec(s)",
                    athleteId, created, updated, cancelled, failed);
        }
        return new Result(created, updated, cancelled, failed, false);
    }

    /**
     * Crée ou réécrit la séance. Si COROS refuse la forme structurée, renvoie la forme simple :
     * une séance lisible sur la montre vaut mieux qu'aucune.
     *
     * @return le nouvel {@code idInPlan}, ou null si COROS ne l'a pas renvoyé
     */
    private String send(Endpoints e, String token, CorosWorkoutSync row, boolean rewriting,
                        Map<String, Object> course, GarminWorkoutMapper.Source source, Vocabulary v) {
        try {
            return rewriting ? update(e, token, row, course) : create(e, token, row.getScheduledDate(), course);
        } catch (Refused ex) {
            Map<String, Object> simple;
            try {
                simple = CorosWorkoutMapper.toSimpleCourse(source, v);
            } catch (IllegalArgumentException none) {
                throw ex;
            }
            if (simple.equals(course)) {
                throw ex;
            }
            log.warn("COROS a refusé la séance structurée ({}), envoi de la forme simple", ex.getMessage());
            return rewriting ? update(e, token, row, simple) : create(e, token, row.getScheduledDate(), simple);
        }
    }

    private String create(Endpoints e, String token, LocalDate date, Map<String, Object> course) {
        JsonNode r = call(e, token, CREATE, Map.of("date", date.format(COROS_DATE), "course", course));
        return idInPlanOf(r);
    }

    private String update(Endpoints e, String token, CorosWorkoutSync row, Map<String, Object> course) {
        JsonNode r = call(e, token, UPDATE, Map.of(
                "date", row.getScheduledDate().format(COROS_DATE),
                "idInPlan", row.getIdInPlan(),
                "course", course));
        // COROS peut changer l'idInPlan à la réécriture : on garde le nouveau s'il est donné.
        return idInPlanOf(r);
    }

    private JsonNode call(Endpoints e, String token, String tool, Map<String, Object> args) {
        JsonNode r;
        try {
            r = client.callTool(e, token, tool, args);
        } catch (RestClientResponseException ex) {
            int status = ex.getStatusCode().value();
            if (status == 401 || status == 403) {
                throw new Unauthorized();
            }
            throw new IllegalStateException("COROS a répondu " + status);
        } catch (CorosMcpClient.McpException ex) {
            throw new Refused(ex.getMessage());
        }
        if (r != null && r.path("isError").asBoolean(false)) {
            throw new Refused(textOf(r));
        }
        return r;
    }

    /** L'{@code idInPlan} renvoyé, dans le contenu structuré ou, à défaut, dans le texte. */
    static String idInPlanOf(JsonNode result) {
        if (result == null) {
            return null;
        }
        String found = findField(result.get("structuredContent"), "idInPlan");
        if (found != null) {
            return found;
        }
        Matcher m = ID_IN_PLAN.matcher(textOf(result));
        return m.find() ? m.group(1) : null;
    }

    private static String findField(JsonNode node, String field) {
        if (node == null) {
            return null;
        }
        if (node.isObject()) {
            JsonNode v = node.get(field);
            if (v != null && (v.isTextual() || v.isNumber()) && !v.asText().isBlank()) {
                return v.asText();
            }
            Iterator<JsonNode> it = node.elements();
            while (it.hasNext()) {
                String f = findField(it.next(), field);
                if (f != null) {
                    return f;
                }
            }
        } else if (node.isArray()) {
            for (JsonNode child : node) {
                String f = findField(child, field);
                if (f != null) {
                    return f;
                }
            }
        }
        return null;
    }

    /** Texte des blocs {@code content} d'un résultat d'outil MCP. */
    static String textOf(JsonNode result) {
        StringBuilder sb = new StringBuilder();
        for (JsonNode c : result.path("content")) {
            if (c.path("text").isTextual()) {
                sb.append(c.path("text").asText()).append('\n');
            }
        }
        String s = sb.toString().strip();
        return s.isEmpty() ? "refus sans détail" : s;
    }

    /** Codes du {@code course} pour ce serveur : lus dans {@code tools/list}, gardés quelques heures. */
    private Vocabulary vocabulary(Endpoints e, String token) {
        CachedVocabulary c = vocabularies.get(e.issuer());
        if (c != null && c.at().plus(VOCABULARY_TTL).isAfter(Instant.now())) {
            return c.vocabulary();
        }
        Vocabulary v = Vocabulary.DEFAULT;
        try {
            JsonNode schema = client.toolInputSchema(e, token, CREATE);
            if (schema != null) {
                v = Vocabulary.fromSchema(schema);
                log.info("COROS ({}) : schéma de {} lu, codes retenus {}", e.issuer(), CREATE, v);
                log.debug("COROS ({}) : schéma complet de {} : {}", e.issuer(), CREATE, schema);
            } else {
                log.warn("COROS ({}) : outil {} absent de tools/list, codes par défaut", e.issuer(), CREATE);
            }
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().value() == 401 || ex.getStatusCode().value() == 403) {
                throw new Unauthorized();
            }
            log.warn("COROS ({}) : tools/list illisible, codes par défaut : {}", e.issuer(), ex.getMessage());
        } catch (RuntimeException ex) {
            log.warn("COROS ({}) : tools/list illisible, codes par défaut : {}", e.issuer(), ex.getMessage());
        }
        vocabularies.put(e.issuer(), new CachedVocabulary(v, Instant.now()));
        return v;
    }

    private static CorosWorkoutSync takeReusable(List<CorosWorkoutSync> reusable, LocalDate date) {
        for (Iterator<CorosWorkoutSync> it = reusable.iterator(); it.hasNext(); ) {
            CorosWorkoutSync r = it.next();
            if (r.getScheduledDate().equals(date) && r.getIdInPlan() != null) {
                it.remove();
                return r;
            }
        }
        return null;
    }

    private static String key(UUID workoutId, LocalDate date) {
        return workoutId + "@" + date;
    }

    private CorosWorkoutSync newRow(UUID athleteId, Workout w) {
        CorosWorkoutSync row = new CorosWorkoutSync();
        row.setAthleteId(athleteId);
        row.setWorkoutId(w.getId());
        row.setScheduledDate(w.getScheduledDate());
        row.setTitle(truncate(w.getTitle(), 120));
        return row;
    }

    private void markError(CorosWorkoutSync row, String message) {
        row.setLastError(truncate(message == null ? "Erreur inconnue" : message, 500));
        syncRepository.save(row);
    }

    private GarminWorkoutMapper.Source sourceOf(Workout w) {
        return new GarminWorkoutMapper.Source(
                w.getTitle(), w.getNotes(), readCalculated(w), w.getSteps(),
                w.getTargetDistanceM(), w.getTargetDurationS());
    }

    private CalculatedSessionResponse readCalculated(Workout w) {
        if (w.getCalculatedPaces() == null || w.getCalculatedPaces().isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(w.getCalculatedPaces(), CalculatedSessionResponse.class);
        } catch (Exception ex) {
            log.debug("Cibles calculées illisibles pour la séance {}", w.getId());
            return null;
        }
    }

    private String hash(Map<String, Object> course, LocalDate date) {
        try {
            String json = objectMapper.writeValueAsString(course) + "|" + date;
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(json.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException | com.fasterxml.jackson.core.JsonProcessingException ex) {
            throw new IllegalStateException("Empreinte impossible", ex);
        }
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max);
    }

    /** Jeton refusé par COROS : inutile d'insister pour cet athlète. */
    private static final class Unauthorized extends RuntimeException {
        Unauthorized() {
            super("COROS a refusé l'accès : reconnecte ton compte COROS.");
        }
    }

    /** COROS a répondu, mais en refusant la séance (contenu invalide, date hors fenêtre…). */
    private static final class Refused extends RuntimeException {
        Refused(String message) {
            super("Refus de COROS : " + message);
        }
    }
}
