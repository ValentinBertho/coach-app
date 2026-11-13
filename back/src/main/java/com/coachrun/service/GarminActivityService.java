package com.coachrun.service;

import com.coachrun.dto.request.ActivityImportRequest;
import com.coachrun.dto.response.ActivityResponse;
import com.coachrun.entity.enums.ActivitySource;
import com.coachrun.entity.enums.DeviceProvider;
import com.coachrun.exception.ConflictException;
import com.coachrun.integration.GarminActivityMapper;
import com.coachrun.integration.GarminActivityMapper.Parsed;
import com.coachrun.repository.AthleteRepository;
import com.coachrun.repository.DeviceConnectionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Import des sorties Garmin, poussées par Garmin (Activity API) dès que la montre se synchronise.
 *
 * <p>Rien n'est demandé à Garmin : le contenu est dans la notification. Deux notifications
 * arrivent pour une même sortie — le résumé ({@code activities}), puis le détail avec tracé, FC
 * et tours ({@code activityDetails}) — dans un ordre que Garmin ne garantit pas. La première crée
 * la sortie, la seconde la complète.</p>
 *
 * <p><b>Une montre, deux chemins.</b> Beaucoup d'athlètes ont aussi Strava, que leur montre
 * Garmin alimente : la même sortie arrive alors deux fois. On n'en garde qu'une. Celle qui
 * arrive la première reste (Garmin, en pratique : il pousse dès la synchro de la montre) ;
 * la seconde ne crée rien, mais comble ce qui manquait à la première — les tours de la montre,
 * typiquement, sans toucher au titre, au rapprochement ni au ressenti.</p>
 *
 * <p>Seuls les athlètes qui ont accordé {@code ACTIVITY_EXPORT} sont concernés : une notification
 * pour un compte qui l'a retirée est ignorée.</p>
 */
@Slf4j
@Service
public class GarminActivityService {

    private final DeviceConnectionRepository connectionRepository;
    private final AthleteRepository athleteRepository;
    private final ActivityService activityService;
    private final NotificationService notificationService;
    private final TransactionTemplate tx;

    public GarminActivityService(DeviceConnectionRepository connectionRepository,
                                 AthleteRepository athleteRepository,
                                 ActivityService activityService,
                                 NotificationService notificationService,
                                 PlatformTransactionManager transactionManager) {
        this.connectionRepository = connectionRepository;
        this.athleteRepository = athleteRepository;
        this.activityService = activityService;
        this.notificationService = notificationService;
        // Pas de transaction englobante : chaque import a la sienne, et un doublon refusé
        // (ConflictException) ne doit pas condamner les autres sorties de la notification.
        this.tx = new TransactionTemplate(transactionManager);
    }

    /** Ce que la notification a produit, pour les journaux et les tests. */
    public record Outcome(int imported, int completed, int ignored) {
    }

    /**
     * Sorties d'une notification pour un même utilisateur Garmin.
     *
     * @param details {@code true} pour {@code activityDetails}, {@code false} pour {@code activities}
     */
    public Outcome handle(String garminUserId, List<Map<String, Object>> items, boolean details) {
        int imported = 0;
        int completed = 0;
        int ignored = 0;
        for (UUID athleteId : connectionRepository
                .findAthleteIdsByProviderAndProviderAthleteId(DeviceProvider.GARMIN, garminUserId)) {
            if (!mayImport(athleteId)) {
                ignored += items.size();
                continue;
            }
            List<StravaService.ImportedActivity> fresh = new ArrayList<>();
            for (Map<String, Object> item : items) {
                Parsed p = details ? GarminActivityMapper.fromDetails(item) : GarminActivityMapper.fromSummary(item);
                switch (importOne(athleteId, p, fresh)) {
                    case IMPORTED -> imported++;
                    case COMPLETED -> completed++;
                    default -> ignored++;
                }
            }
            notify(athleteId, fresh);
        }
        if (imported + completed > 0) {
            log.info("Garmin : {} sortie(s) importée(s), {} complétée(s)", imported, completed);
        }
        return new Outcome(imported, completed, ignored);
    }

    private enum Result { IMPORTED, COMPLETED, IGNORED }

    private Result importOne(UUID athleteId, Parsed p, List<StravaService.ImportedActivity> fresh) {
        // Une activité « parente » (multisport) ne porte rien de plus que ses enfants, qui
        // arrivent chacun de leur côté : la garder compterait le triathlon deux fois.
        if (p == null || p.parent()) {
            return Result.IGNORED;
        }
        ActivityService.ImportExtras extras = new ActivityService.ImportExtras(
                p.maxHr(), p.avgCadence(), p.avgPowerW(), p.calories(), p.route(), p.stream(), p.laps());

        // Déjà là, venue de Garmin : c'est l'autre moitié (résumé ↔ détails).
        Optional<UUID> mine = activityService.findImported(athleteId, ActivitySource.GARMIN, p.externalId());
        if (mine.isPresent()) {
            return activityService.completeWith(mine.get(), p.avgHr(), p.elevationGainM(), extras)
                    ? Result.COMPLETED : Result.IGNORED;
        }
        // Écartée pour de bon par l'athlète.
        if (activityService.alreadyKnown(athleteId, ActivitySource.GARMIN, p.externalId())) {
            return Result.IGNORED;
        }
        // La même sortie, déjà arrivée par Strava ou par un fichier : on complète, on ne double pas.
        Optional<UUID> twin = activityService.findSameOuting(athleteId, p.date(), p.distanceM(), p.durationS());
        if (twin.isPresent()) {
            return activityService.completeWith(twin.get(), p.avgHr(), p.elevationGainM(), extras)
                    ? Result.COMPLETED : Result.IGNORED;
        }
        try {
            UUID clubId = clubIdOf(athleteId);
            if (clubId == null) {
                return Result.IGNORED;
            }
            ActivityResponse saved = activityService.importActivity(clubId, athleteId, new ActivityImportRequest(
                    ActivitySource.GARMIN, p.externalId(), p.date(), p.title(),
                    p.distanceM(), p.durationS(), p.avgHr(), p.elevationGainM(),
                    p.sport(), p.activityType() == null ? null : p.activityType().toLowerCase(java.util.Locale.ROOT),
                    false), extras);
            fresh.add(new StravaService.ImportedActivity(saved.title(), p.durationS(), p.distanceM()));
            return Result.IMPORTED;
        } catch (ConflictException ex) {
            // Course entre deux notifications : l'autre a gagné, rien à faire.
            return Result.IGNORED;
        }
    }

    private boolean mayImport(UUID athleteId) {
        return connectionRepository.findByAthleteIdAndProvider(athleteId, DeviceProvider.GARMIN)
                .map(GarminService::permissionsOf)
                .map(perms -> perms.contains(GarminService.ACTIVITY_EXPORT))
                .orElse(false);
    }

    private UUID clubIdOf(UUID athleteId) {
        return tx.execute(status -> athleteRepository.findById(athleteId)
                .map(a -> a.getClub() == null ? null : a.getClub().getId())
                .orElse(null));
    }

    /** Une notification pour tout le lot, jamais une par sortie — comme pour Strava. */
    private void notify(UUID athleteId, List<StravaService.ImportedActivity> fresh) {
        if (fresh.isEmpty()) {
            return;
        }
        try {
            tx.executeWithoutResult(status -> athleteRepository.findById(athleteId)
                    .ifPresent(a -> notificationService.notifyActivitiesImported(a, fresh)));
        } catch (RuntimeException ex) {
            // La sortie est enregistrée ; seule la notification manque.
            log.warn("Notification d'import Garmin non envoyée : {}", ex.getMessage());
        }
    }
}
