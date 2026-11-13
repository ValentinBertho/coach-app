package com.coachrun.service;

import com.coachrun.dto.request.ActivityImportRequest;
import com.coachrun.dto.response.ActivityResponse;
import com.coachrun.entity.CorosActivityImport;
import com.coachrun.entity.CorosActivityImport.Outcome;
import com.coachrun.entity.DeviceConnection;
import com.coachrun.entity.enums.ActivitySource;
import com.coachrun.entity.enums.ActivitySport;
import com.coachrun.entity.enums.DeviceProvider;
import com.coachrun.exception.ConflictException;
import com.coachrun.integration.CorosActivityRecords;
import com.coachrun.integration.CorosActivityRecords.Rec;
import com.coachrun.integration.CorosMcpClient;
import com.coachrun.integration.CorosMcpClient.Endpoints;
import com.coachrun.repository.AthleteRepository;
import com.coachrun.repository.CorosActivityImportRepository;
import com.coachrun.repository.DeviceConnectionRepository;
import com.coachrun.util.ActivityFileParser;
import com.coachrun.util.ActivityTrack.ParsedActivity;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestClientResponseException;

/**
 * Import des sorties COROS (COROS MCP), par interrogation : COROS ne prévient de rien.
 *
 * <p>À chaque passe, pour un athlète connecté : la liste de ses sorties récentes
 * ({@code querySportRecords}), puis, pour chaque sortie encore inconnue, son fichier FIT
 * ({@code queryActivityFitFileDownloadUrls}, à défaut {@code downloadActivityFitFiles}), lu par
 * le même analyseur que l'import de fichier.</p>
 *
 * <p><b>Quota.</b> COROS autorise 50 fichiers FIT par utilisateur et par 24 h. On s'arrête à
 * {@link #DAILY_DOWNLOADS} pour garder de la marge, et une sortie déjà traitée n'est jamais
 * retéléchargée. Une sortie dont Strava ou Garmin a déjà apporté le double n'est pas
 * téléchargée du tout : c'est le cas courant, et le quota n'y suffirait pas.</p>
 *
 * <p><b>Doublons.</b> Comme pour Garmin : la première arrivée reste. Une sortie déjà là sous la
 * même date, distance et durée n'est pas recréée.</p>
 */
@Slf4j
@Service
public class CorosActivityService {

    /** Premier import : les 30 derniers jours. */
    public static final int FIRST_IMPORT_DAYS = 30;
    /** Passes suivantes : les 3 derniers jours, pour une montre synchronisée en retard. */
    public static final int RECENT_DAYS = 3;
    /** Téléchargements FIT par 24 h : COROS en autorise 50, on garde de la marge. */
    public static final int DAILY_DOWNLOADS = 40;
    static final int MAX_ATTEMPTS = 3;
    static final int RECORDS_LIMIT = 50;
    static final String LIST = "querySportRecords";
    static final String URLS = "queryActivityFitFileDownloadUrls";
    static final String DOWNLOAD = "downloadActivityFitFiles";

    private final CorosMcpClient client;
    private final CorosService corosService;
    private final DeviceConnectionRepository connectionRepository;
    private final CorosActivityImportRepository importRepository;
    private final AthleteRepository athleteRepository;
    private final ActivityService activityService;
    private final NotificationService notificationService;
    private final ClockService clock;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate tx;

    private final Set<UUID> inFlight = ConcurrentHashMap.newKeySet();

    public CorosActivityService(CorosMcpClient client, CorosService corosService,
                                DeviceConnectionRepository connectionRepository,
                                CorosActivityImportRepository importRepository,
                                AthleteRepository athleteRepository,
                                ActivityService activityService,
                                NotificationService notificationService,
                                ClockService clock, ObjectMapper objectMapper,
                                PlatformTransactionManager transactionManager) {
        this.client = client;
        this.corosService = corosService;
        this.connectionRepository = connectionRepository;
        this.importRepository = importRepository;
        this.athleteRepository = athleteRepository;
        this.activityService = activityService;
        this.notificationService = notificationService;
        this.clock = clock;
        this.objectMapper = objectMapper;
        // Pas de transaction englobante : chaque sortie a la sienne, et un échec ne doit pas
        // condamner les autres.
        this.tx = new TransactionTemplate(transactionManager);
    }

    /** Bilan d'une passe, pour l'écran et les journaux. */
    public record Result(int imported, int merged, int failed, boolean quotaReached, boolean skipped) {
        static Result skippedResult() {
            return new Result(0, 0, 0, false, true);
        }
    }

    /** Importe les nouvelles sorties COROS de l'athlète. */
    public Result importAthlete(UUID athleteId) {
        if (!client.isConfigured() || !inFlight.add(athleteId)) {
            return Result.skippedResult();
        }
        try {
            DeviceConnection conn = connectionRepository
                    .findByAthleteIdAndProvider(athleteId, DeviceProvider.COROS).orElse(null);
            if (conn == null) {
                return Result.skippedResult();
            }
            return run(athleteId, conn);
        } finally {
            inFlight.remove(athleteId);
        }
    }

    /** Sorties créées dans Darilab depuis COROS. */
    public long importedCount(UUID athleteId) {
        return importRepository.countImported(athleteId);
    }

    /** À la déconnexion : on oublie ce qu'on savait du compte COROS. Les sorties restent. */
    public void forgetAthlete(UUID athleteId) {
        tx.executeWithoutResult(s -> importRepository.deleteByAthleteId(athleteId));
    }

    private Result run(UUID athleteId, DeviceConnection conn) {
        String token;
        Endpoints e;
        try {
            token = corosService.freshAccessToken(conn);
            e = corosService.endpointsOf(conn);
        } catch (RuntimeException ex) {
            log.warn("COROS : jeton indisponible pour l'athlète {} : {}", athleteId, ex.getMessage());
            return new Result(0, 0, 1, false, false);
        }

        LocalDate today = clock.today();
        boolean first = !importRepository.existsByAthleteId(athleteId);
        LocalDate from = today.minusDays(first ? FIRST_IMPORT_DAYS : RECENT_DAYS);
        List<Rec> records;
        try {
            JsonNode r = call(e, token, LIST, Map.of(
                    "startDate", from.format(CorosActivityRecords.COROS_DATE),
                    "endDate", today.format(CorosActivityRecords.COROS_DATE),
                    "limit", RECORDS_LIMIT));
            records = CorosActivityRecords.records(objectMapper, r, clock.zone());
            if (records.isEmpty() && !CorosActivityRecords.textOf(r).isBlank()) {
                // Sans labelId, rien ne peut être téléchargé : le format a dû changer.
                log.info("COROS : aucune sortie lisible dans la réponse de {} pour l'athlète {} : {}", LIST,
                        athleteId, abbreviate(CorosActivityRecords.textOf(r), 600));
            }
        } catch (Unauthorized ex) {
            return new Result(0, 0, 1, false, false);
        } catch (RuntimeException ex) {
            log.warn("COROS : liste des sorties illisible pour l'athlète {} : {}", athleteId, ex.getMessage());
            return new Result(0, 0, 1, false, false);
        }

        // Les plus anciennes d'abord : si le quota coupe, ce sont les plus récentes qui attendent
        // la passe suivante, et l'ordre d'arrivée reste celui des sorties.
        records = new ArrayList<>(records);
        records.sort(Comparator.comparing(Rec::date, Comparator.nullsLast(Comparator.naturalOrder())));

        int imported = 0;
        int merged = 0;
        int failed = 0;
        boolean quota = false;
        List<StravaService.ImportedActivity> fresh = new ArrayList<>();
        for (Rec rec : records) {
            CorosActivityImport row = importRepository.findByAthleteIdAndLabelId(athleteId, rec.labelId()).orElse(null);
            if (row != null && (row.getOutcome() != Outcome.FAILED || row.getAttempts() >= MAX_ATTEMPTS)) {
                continue; // déjà traitée, ou abandonnée après plusieurs échecs
            }
            if (row == null) {
                row = newRow(athleteId, rec);
            }
            // Déjà importée depuis COROS (avant une déconnexion), ou écartée par l'athlète.
            Optional<UUID> mine = activityService.findImported(athleteId, ActivitySource.COROS, rec.labelId());
            if (mine.isPresent() || activityService.alreadyKnown(athleteId, ActivitySource.COROS, rec.labelId())) {
                finish(row, Outcome.SKIPPED, mine.orElse(null));
                continue;
            }
            // Même sortie déjà arrivée par Strava ou Garmin : rien à télécharger.
            if (rec.date() != null && (rec.distanceM() != null || rec.durationS() != null)) {
                Optional<UUID> twin = activityService.findSameOuting(athleteId, rec.date(), rec.distanceM(),
                        rec.durationS());
                if (twin.isPresent()) {
                    finish(row, Outcome.MERGED, twin.get());
                    merged++;
                    continue;
                }
            }
            if (rec.sportType() == null) {
                fail(row, "Code sport COROS absent : fichier non téléchargeable.");
                failed++;
                continue;
            }
            if (importRepository.countByAthleteIdAndDownloadedAtAfter(athleteId,
                    Instant.now().minus(Duration.ofHours(24))) >= DAILY_DOWNLOADS) {
                quota = true;
                break;
            }
            try {
                byte[] fit = download(e, token, row, rec);
                ParsedActivity parsed = ActivityFileParser.parse(fit, clock.zone());
                switch (store(athleteId, row, rec, parsed, fresh)) {
                    case IMPORTED -> imported++;
                    case MERGED -> merged++;
                    default -> { }
                }
            } catch (Unauthorized ex) {
                fail(row, ex.getMessage());
                failed++;
                break;
            } catch (RuntimeException ex) {
                fail(row, ex.getMessage());
                failed++;
            }
        }
        notify(athleteId, fresh);
        if (imported + merged + failed > 0 || quota) {
            log.info("COROS athlète {} : {} sortie(s) importée(s), {} déjà là, {} échec(s){}", athleteId,
                    imported, merged, failed, quota ? ", quota de téléchargement atteint" : "");
        }
        return new Result(imported, merged, failed, quota, false);
    }

    /** Le FIT de la sortie : par lien d'abord, joint à la réponse sinon. */
    private byte[] download(Endpoints e, String token, CorosActivityImport row, Rec rec) {
        Map<String, Object> args = Map.of("labelId", rec.labelId(), "sportType", rec.sportType(), "limit", 1);
        // Compté dans le quota dès la demande : COROS le décompte même si le fichier est illisible.
        row.setDownloadedAt(Instant.now());
        save(row);
        JsonNode urls = call(e, token, URLS, args);
        for (String url : CorosActivityRecords.fitUrls(objectMapper, urls)) {
            byte[] bytes = client.downloadFit(url, token);
            if (bytes != null && bytes.length > 0) {
                return bytes;
            }
        }
        List<byte[]> blobs = CorosActivityRecords.fitBlobs(call(e, token, DOWNLOAD, args));
        if (!blobs.isEmpty()) {
            return blobs.get(0);
        }
        log.info("COROS : aucun fichier pour la sortie {} : {}", rec.labelId(),
                abbreviate(CorosActivityRecords.textOf(urls), 400));
        throw new IllegalStateException("COROS n'a pas fourni le fichier de la sortie.");
    }

    private Outcome store(UUID athleteId, CorosActivityImport row, Rec rec, ParsedActivity p,
                          List<StravaService.ImportedActivity> fresh) {
        ActivityService.ImportExtras extras = new ActivityService.ImportExtras(
                null, null, null, null, p.route(), p.stream(), p.laps());
        Optional<UUID> twin = activityService.findSameOuting(athleteId, p.date(), p.distanceM(), p.durationS());
        if (twin.isPresent()) {
            // Arrivée d'abord par un autre chemin : on comble ce qui lui manque (tours, tracé).
            activityService.completeWith(twin.get(), p.avgHr(), p.elevationGainM(), extras);
            finish(row, Outcome.MERGED, twin.get());
            return Outcome.MERGED;
        }
        UUID clubId = clubIdOf(athleteId);
        if (clubId == null) {
            finish(row, Outcome.SKIPPED, null);
            return Outcome.SKIPPED;
        }
        try {
            String title = rec.name() != null && !rec.name().isBlank() ? rec.name().trim() : titleOf(p.sport());
            ActivityResponse saved = activityService.importActivity(clubId, athleteId, new ActivityImportRequest(
                    ActivitySource.COROS, rec.labelId(), p.date(), title,
                    p.distanceM(), p.durationS(), p.avgHr(), p.elevationGainM(),
                    p.sport(), p.sportDetail(), false), extras);
            finish(row, Outcome.IMPORTED, saved.id());
            fresh.add(new StravaService.ImportedActivity(saved.title(), p.durationS(), p.distanceM()));
            return Outcome.IMPORTED;
        } catch (ConflictException ex) {
            // Arrivée entre-temps par un autre chemin : l'autre a gagné.
            finish(row, Outcome.MERGED, null);
            return Outcome.MERGED;
        }
    }

    static String titleOf(ActivitySport sport) {
        if (sport == null) {
            return "Sortie COROS";
        }
        return switch (sport) {
            case RUN -> "Course à pied";
            case RIDE -> "Vélo";
            case SWIM -> "Natation";
            case WALK -> "Marche";
            case STRENGTH -> "Renforcement";
            case OTHER -> "Sortie COROS";
        };
    }

    private JsonNode call(Endpoints e, String token, String tool, Map<String, Object> args) {
        JsonNode r;
        try {
            r = client.callTool(e, token, tool, new LinkedHashMap<>(args));
        } catch (RestClientResponseException ex) {
            int status = ex.getStatusCode().value();
            if (status == 401 || status == 403) {
                throw new Unauthorized();
            }
            throw new IllegalStateException("COROS a répondu " + status);
        } catch (CorosMcpClient.McpException ex) {
            throw new IllegalStateException("COROS a refusé : " + ex.getMessage());
        }
        if (r != null && r.path("isError").asBoolean(false)) {
            throw new IllegalStateException("COROS a refusé : " + abbreviate(CorosActivityRecords.textOf(r), 200));
        }
        return r;
    }

    private CorosActivityImport newRow(UUID athleteId, Rec rec) {
        CorosActivityImport row = new CorosActivityImport();
        row.setAthleteId(athleteId);
        row.setLabelId(rec.labelId());
        row.setSportType(rec.sportType());
        row.setActivityDate(rec.date());
        row.setOutcome(Outcome.FAILED);
        return row;
    }

    private void finish(CorosActivityImport row, Outcome outcome, UUID activityId) {
        row.setOutcome(outcome);
        row.setActivityId(activityId);
        row.setLastError(null);
        save(row);
    }

    private void fail(CorosActivityImport row, String message) {
        row.setOutcome(Outcome.FAILED);
        row.setAttempts(row.getAttempts() + 1);
        row.setLastError(abbreviate(message == null ? "Erreur inconnue" : message, 500));
        save(row);
    }

    private void save(CorosActivityImport row) {
        CorosActivityImport saved = tx.execute(s -> importRepository.save(row));
        if (saved != null && row.getId() == null) {
            row.setId(saved.getId());
        }
        if (saved != null) {
            row.setVersion(saved.getVersion());
        }
    }

    private UUID clubIdOf(UUID athleteId) {
        return tx.execute(status -> athleteRepository.findById(athleteId)
                .map(a -> a.getClub() == null ? null : a.getClub().getId())
                .orElse(null));
    }

    /** Une notification pour tout le lot, comme pour Strava et Garmin. */
    private void notify(UUID athleteId, List<StravaService.ImportedActivity> fresh) {
        if (fresh.isEmpty()) {
            return;
        }
        try {
            tx.executeWithoutResult(status -> athleteRepository.findById(athleteId)
                    .ifPresent(a -> notificationService.notifyActivitiesImported(a, fresh)));
        } catch (RuntimeException ex) {
            log.warn("Notification d'import COROS non envoyée : {}", ex.getMessage());
        }
    }

    private static String abbreviate(String s, int max) {
        if (s == null) {
            return "";
        }
        String t = s.strip();
        return t.length() <= max ? t : t.substring(0, max - 1) + "…";
    }

    private static final class Unauthorized extends RuntimeException {
        Unauthorized() {
            super("COROS a refusé l'accès : reconnecte ton compte COROS.");
        }
    }
}
