package com.coachrun.service;

import com.coachrun.dto.response.CalculatedSessionResponse;
import com.coachrun.entity.DeviceConnection;
import com.coachrun.entity.GarminWorkoutSync;
import com.coachrun.entity.Workout;
import com.coachrun.entity.enums.DeviceProvider;
import com.coachrun.entity.enums.WorkoutStatus;
import com.coachrun.entity.enums.WorkoutType;
import com.coachrun.integration.GarminClient;
import com.coachrun.integration.GarminWorkoutMapper;
import com.coachrun.repository.DeviceConnectionRepository;
import com.coachrun.repository.GarminWorkoutSyncRepository;
import com.coachrun.repository.WorkoutRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpClientErrorException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Envoi des séances course prescrites dans le calendrier Garmin de l'athlète (Training API).
 *
 * <h2>Une réconciliation, pas des événements</h2>
 *
 * <p>Une séance naît, change ou disparaît par une dizaine de chemins (assignation, plan appliqué,
 * groupe, copie, glisser-déposer, suppression…). Accrocher un envoi à chacun serait fragile : il
 * suffirait d'en oublier un pour qu'une montre affiche une séance périmée. On compare donc, pour
 * chaque athlète, ce qui <b>devrait</b> être chez Garmin (ses séances course des
 * {@link #WINDOW_DAYS} prochains jours) à ce qui <b>y est</b> ({@link GarminWorkoutSync}), et on
 * corrige l'écart. Le même passage gère la création, la modification, le déplacement et la
 * suppression, et rattrape seul un envoi qui avait échoué.</p>
 *
 * <p>Quota Starter : 200 appels par athlète et par jour. L'empreinte du contenu envoyé évite tout
 * appel tant qu'une séance ne change pas ; une séance nouvelle coûte deux appels (création +
 * planification), une séance modifiée trois (suppression + création + planification).</p>
 *
 * <p>Les séances passées ne sont jamais retirées de Garmin : elles font partie de l'historique de
 * l'athlète là-bas. On oublie seulement leur suivi.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GarminWorkoutSyncService {

    /** Horizon envoyé : aujourd'hui et les 13 jours suivants. */
    public static final int WINDOW_DAYS = 14;

    /** Types de séance qui ne sont pas de la course : rien à mettre sur la montre. */
    private static final Set<WorkoutType> NOT_RUNNING =
            Set.of(WorkoutType.STRENGTH, WorkoutType.CROSS_TRAINING, WorkoutType.REST);

    private final GarminClient client;
    private final GarminService garminService;
    private final DeviceConnectionRepository connectionRepository;
    private final GarminWorkoutSyncRepository syncRepository;
    private final WorkoutRepository workoutRepository;
    private final ClockService clock;
    private final ObjectMapper objectMapper;

    /** Athlètes en cours de synchronisation : un clic pendant la passe planifiée ne double rien. */
    private final Set<UUID> inFlight = ConcurrentHashMap.newKeySet();

    /** Bilan d'une synchronisation, pour l'écran et les journaux. */
    public record Result(int created, int updated, int removed, int failed, boolean skipped) {
        static Result skippedResult() {
            return new Result(0, 0, 0, 0, true);
        }
    }

    /** Une séance course éligible à l'envoi ? */
    public static boolean eligible(Workout w, LocalDate today) {
        return w.getStatus() == WorkoutStatus.PLANNED
                && w.getType() != null && !NOT_RUNNING.contains(w.getType())
                && w.getScheduledDate() != null
                && !w.getScheduledDate().isBefore(today)
                && w.getScheduledDate().isBefore(today.plusDays(WINDOW_DAYS));
    }

    /**
     * Met le calendrier Garmin de l'athlète en accord avec ses séances à venir.
     *
     * <p>Ne lève jamais pour une séance en échec : l'erreur est notée sur sa ligne et la suivante
     * est traitée. Une ligne n'est enregistrée qu'<b>après</b> l'appel qui la justifie, pour ne
     * jamais perdre la trace d'un entraînement créé là-bas — sinon il y resterait en double.</p>
     */
    @Transactional
    public Result syncAthlete(UUID athleteId) {
        if (!client.isConfigured() || !inFlight.add(athleteId)) {
            return Result.skippedResult();
        }
        try {
            DeviceConnection conn = connectionRepository
                    .findByAthleteIdAndProvider(athleteId, DeviceProvider.GARMIN).orElse(null);
            if (conn == null || !GarminService.permissionsOf(conn).contains(GarminService.WORKOUT_IMPORT)) {
                return Result.skippedResult();
            }
            return reconcile(athleteId, conn);
        } finally {
            inFlight.remove(athleteId);
        }
    }

    private Result reconcile(UUID athleteId, DeviceConnection conn) {
        LocalDate today = clock.today();
        String token;
        try {
            token = garminService.freshAccessToken(conn);
        } catch (RuntimeException e) {
            log.warn("Garmin : jeton indisponible pour l'athlète {} : {}", athleteId, e.getMessage());
            return new Result(0, 0, 0, 1, false);
        }

        Map<UUID, Workout> wanted = new HashMap<>();
        for (Workout w : workoutRepository.findByAthleteIdAndScheduledDateBetweenOrderByScheduledDateAsc(
                athleteId, today, today.plusDays(WINDOW_DAYS - 1))) {
            if (eligible(w, today)) {
                wanted.put(w.getId(), w);
            }
        }

        int created = 0;
        int updated = 0;
        int removed = 0;
        int failed = 0;

        // 1. Ce qui est chez Garmin et ne devrait plus y être, ou qui a changé.
        Map<UUID, GarminWorkoutSync> kept = new HashMap<>();
        for (GarminWorkoutSync row : syncRepository.findByAthleteId(athleteId)) {
            Workout w = row.getWorkoutId() != null ? wanted.get(row.getWorkoutId()) : null;
            if (w == null) {
                if (row.getScheduledDate().isBefore(today)) {
                    syncRepository.delete(row); // passée : elle reste dans l'historique Garmin
                    continue;
                }
                try {
                    removeFromGarmin(token, row);
                    syncRepository.delete(row);
                    removed++;
                } catch (RateLimited e) {
                    return new Result(created, updated, removed, failed + 1, false);
                } catch (RuntimeException e) {
                    failed++;
                    markError(row, "Retrait impossible : " + e.getMessage());
                }
                continue;
            }
            kept.put(w.getId(), row);
        }

        // 2. Ce qui doit y être : créé, ou recréé si le contenu a changé.
        for (Workout w : wanted.values()) {
            GarminWorkoutSync row = kept.get(w.getId());
            Map<String, Object> payload;
            try {
                payload = GarminWorkoutMapper.toGarmin(sourceOf(w));
            } catch (IllegalArgumentException e) {
                row = row != null ? row : newRow(athleteId, w);
                failed++;
                markError(row, e.getMessage());
                continue;
            }
            String hash = hash(payload, w.getScheduledDate());
            if (row != null && row.getGarminWorkoutId() != null && hash.equals(row.getContentHash())) {
                continue; // inchangée : aucun appel
            }
            boolean replacing = row != null && row.getGarminWorkoutId() != null;
            if (row == null) {
                row = newRow(athleteId, w);
            }
            try {
                if (replacing) {
                    removeFromGarmin(token, row);
                }
                Long garminId = client.createWorkout(token, payload);
                if (garminId == null) {
                    throw new IllegalStateException("Garmin n'a pas renvoyé d'identifiant d'entraînement.");
                }
                // Enregistrer tout de suite : l'entraînement existe chez Garmin, même si la
                // planification échoue juste après.
                row.setGarminWorkoutId(garminId);
                row.setGarminScheduleId(null);
                row.setScheduledDate(w.getScheduledDate());
                row.setContentHash(null);
                syncRepository.save(row);

                row.setGarminScheduleId(client.scheduleWorkout(token, garminId, w.getScheduledDate()));
                row.setContentHash(hash);
                row.setLastError(null);
                syncRepository.save(row);
                if (replacing) {
                    updated++;
                } else {
                    created++;
                }
            } catch (RateLimited e) {
                failed++;
                markError(row, "Quota Garmin atteint, nouvel essai plus tard.");
                return new Result(created, updated, removed, failed, false);
            } catch (HttpClientErrorException e) {
                if (e.getStatusCode().value() == 429) {
                    failed++;
                    markError(row, "Quota Garmin atteint, nouvel essai plus tard.");
                    return new Result(created, updated, removed, failed, false);
                }
                failed++;
                markError(row, "Refus de Garmin (" + e.getStatusCode().value() + ")");
                if (e.getStatusCode().value() == 401 || e.getStatusCode().value() == 403
                        || e.getStatusCode().value() == 412) {
                    // Permission retirée ou jeton refusé : inutile d'insister sur les suivantes.
                    return new Result(created, updated, removed, failed, false);
                }
            } catch (RuntimeException e) {
                failed++;
                markError(row, "Envoi impossible : " + e.getMessage());
            }
        }

        if (created + updated + removed + failed > 0) {
            log.info("Garmin athlète {} : {} créée(s), {} mise(s) à jour, {} retirée(s), {} échec(s)",
                    athleteId, created, updated, removed, failed);
        }
        return new Result(created, updated, removed, failed, false);
    }

    /**
     * À la déconnexion : retire de Garmin les séances à venir que nous y avions mises, puis oublie
     * leur suivi. Au mieux : un échec ne bloque pas la déconnexion.
     */
    @Transactional
    public void removeAllForAthlete(UUID athleteId, String accessToken) {
        LocalDate today = clock.today();
        List<GarminWorkoutSync> rows = syncRepository.findByAthleteId(athleteId);
        for (GarminWorkoutSync row : rows) {
            if (accessToken != null && !row.getScheduledDate().isBefore(today)) {
                try {
                    removeFromGarmin(accessToken, row);
                } catch (RuntimeException e) {
                    log.debug("Garmin : retrait de l'entraînement {} impossible : {}",
                            row.getGarminWorkoutId(), e.getMessage());
                }
            }
        }
        // Suppression entité par entité, pas en masse : les lignes viennent d'être modifiées en
        // mémoire, et une suppression en masse laisserait des mises à jour orphelines au flush.
        syncRepository.deleteAll(rows);
    }

    /** Synthèse pour l'écran Synchronisation. */
    public Summary summary(UUID athleteId) {
        LocalDate today = clock.today();
        return new Summary(
                syncRepository.countByAthleteIdAndGarminWorkoutIdIsNotNullAndScheduledDateGreaterThanEqual(athleteId, today),
                syncRepository.countByAthleteIdAndLastErrorIsNotNullAndScheduledDateGreaterThanEqual(athleteId, today));
    }

    public record Summary(long workoutsOnGarmin, long workoutsInError) {
    }

    // --- Rouages ------------------------------------------------------------------------------

    private void removeFromGarmin(String token, GarminWorkoutSync row) {
        if (row.getGarminWorkoutId() == null) {
            return;
        }
        try {
            client.deleteWorkout(token, row.getGarminWorkoutId());
        } catch (HttpClientErrorException e) {
            int code = e.getStatusCode().value();
            if (code == 429) {
                throw new RateLimited();
            }
            if (code != 404) {
                throw e; // 404 : l'athlète l'a déjà supprimé lui-même, c'est le résultat voulu
            }
        }
        row.setGarminWorkoutId(null);
        row.setGarminScheduleId(null);
    }

    private GarminWorkoutSync newRow(UUID athleteId, Workout w) {
        GarminWorkoutSync row = new GarminWorkoutSync();
        row.setAthleteId(athleteId);
        row.setWorkoutId(w.getId());
        row.setScheduledDate(w.getScheduledDate());
        return row;
    }

    private void markError(GarminWorkoutSync row, String message) {
        String m = message == null ? "Erreur inconnue" : message;
        row.setLastError(m.length() > 500 ? m.substring(0, 500) : m);
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
        } catch (Exception e) {
            log.debug("Cibles calculées illisibles pour la séance {}", w.getId());
            return null;
        }
    }

    private String hash(Map<String, Object> payload, LocalDate date) {
        try {
            String json = objectMapper.writeValueAsString(payload) + "|" + date;
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(json.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException | com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException("Empreinte impossible", e);
        }
    }

    /** Quota Garmin atteint (429) : on arrête là pour cet athlète, la passe suivante reprendra. */
    private static final class RateLimited extends RuntimeException {
        RateLimited() {
            super("Quota Garmin atteint");
        }
    }
}
