package com.coachrun.scheduler;

import com.coachrun.entity.Athlete;
import com.coachrun.entity.Club;
import com.coachrun.entity.User;
import com.coachrun.repository.AthleteRepository;
import com.coachrun.repository.ClubRepository;
import com.coachrun.service.ClockService;
import com.coachrun.service.CoachDashboardService;
import com.coachrun.service.NotificationService;
import com.coachrun.service.WeeklyRecapService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Les deux rendez-vous hebdomadaires : le bilan de l'athlète le dimanche soir, celui du coach le
 * lundi matin.
 *
 * <p><b>Pourquoi deux horaires et pas un.</b> Ils ne servent pas la même chose. Le dimanche soir,
 * l'athlète vient de finir sa semaine et n'a rien à décider : il regarde ce qu'il a fait. Le lundi
 * matin, le coach ouvre la sienne : son bilan est un point de départ, pas une conclusion, et il
 * doit tomber au moment où il peut encore agir dessus.</p>
 *
 * <p><b>Un club en échec n'arrête pas les autres.</b> Comme le digest d'alertes, le balayage
 * n'est pas transactionnel : chaque club a sa propre transaction, et un club en difficulté est
 * journalisé pendant que le reste continue. Un bilan hebdomadaire partiel vaut mieux qu'un bilan
 * absent — et il ne repassera pas avant sept jours.</p>
 *
 * <p>Protégé par un verrou distribué : un bilan envoyé deux fois est un bilan qu'on cesse de lire.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WeeklyRecapScheduler {

    private final ClubRepository clubRepository;
    private final AthleteRepository athleteRepository;
    private final WeeklyRecapService recapService;
    private final NotificationService notificationService;
    private final CoachDashboardService dashboardService;
    private final ClockService clock;

    /**
     * Référence sur soi via le proxy : {@code @Transactional} est porté par un proxy Spring, et un
     * appel direct {@code this.recapForClub(...)} depuis la méthode planifiée le
     * court-circuiterait — la transaction par club n'existerait alors pas du tout, défaut qui ne
     * se voit ni à la compilation ni dans un test appelant la méthode directement.
     */
    private final ObjectProvider<WeeklyRecapScheduler> self;

    /** Bilan de l'athlète : dimanche 18 h, sur la semaine qui s'achève (lundi → dimanche). */
    @Scheduled(cron = "${app.recap.athlete-cron:0 0 18 * * SUN}")
    @SchedulerLock(name = "sendAthleteWeeklyRecaps", lockAtLeastFor = "PT5M", lockAtMostFor = "PT30M")
    public void sendAthleteWeeklyRecaps() {
        LocalDate to = clock.today().with(DayOfWeek.SUNDAY);
        LocalDate from = to.minusDays(6);
        int sent = 0;
        for (UUID clubId : clubIds()) {
            try {
                sent += self.getObject().athleteRecapsForClub(clubId, from, to);
            } catch (RuntimeException ex) {
                log.error("Bilan athlète en échec pour le club {} — les autres continuent", clubId, ex);
                io.sentry.Sentry.captureException(ex);
            }
        }
        if (sent > 0) {
            log.info("Bilan hebdomadaire envoyé à {} athlète(s).", sent);
        }
    }

    /**
     * Bilan du coach : lundi 7 h 30, sur la semaine écoulée (lundi → dimanche de la veille).
     *
     * <p>Une demi-heure après le digest d'alertes de 7 h, et non en même temps : deux
     * notifications simultanées se lisent comme une seule, et c'est toujours la seconde qu'on
     * perd.</p>
     */
    @Scheduled(cron = "${app.recap.coach-cron:0 30 7 * * MON}")
    @SchedulerLock(name = "sendCoachWeeklyRecaps", lockAtLeastFor = "PT5M", lockAtMostFor = "PT30M")
    public void sendCoachWeeklyRecaps() {
        LocalDate to = clock.today().minusDays(1).with(DayOfWeek.SUNDAY);
        LocalDate from = to.minusDays(6);
        int sent = 0;
        for (UUID clubId : clubIds()) {
            try {
                sent += self.getObject().coachRecapsForClub(clubId, from, to);
            } catch (RuntimeException ex) {
                log.error("Bilan coach en échec pour le club {} — les autres continuent", clubId, ex);
                io.sentry.Sentry.captureException(ex);
            }
        }
        if (sent > 0) {
            log.info("Bilan hebdomadaire envoyé à {} coach(s).", sent);
        }
    }

    // --- Un club à la fois ------------------------------------------------------

    /** @return le nombre d'athlètes effectivement notifiés. */
    @Transactional
    public int athleteRecapsForClub(UUID clubId, LocalDate from, LocalDate to) {
        int sent = 0;
        // Rattachement additionnel compris : un athlète rattaché à un second club recevait son
        // bilan depuis son club principal seulement, et le coach du second ne voyait jamais sa
        // semaine passer.
        for (Athlete athlete : athleteRepository.findByClubMembershipOrderByLastNameAsc(clubId)) {
            WeeklyRecapService.AthleteRecap recap = recapService.forAthlete(athlete.getId(), from, to);
            // Une semaine sans rien : pas de séance prévue, pas un kilomètre. Envoyer « 0 km, 0
            // séance sur 0 » à quelqu'un qui n'a pas couru n'est pas un bilan, c'est un reproche.
            if (recap.isEmpty()) {
                continue;
            }
            if (notificationService.notifyAthleteWeeklyRecap(athlete, recap)) {
                sent++;
            }
        }
        return sent;
    }

    /** @return le nombre de coachs effectivement notifiés. */
    @Transactional
    public int coachRecapsForClub(UUID clubId, LocalDate from, LocalDate to) {
        List<Athlete> athletes = athleteRepository.findByClubMembershipOrderByLastNameAsc(clubId);
        if (athletes.isEmpty()) {
            return 0;
        }

        // Chaque athlète va au coach qui le suit — le même routage que le digest d'alertes, pour
        // qu'un coach ne reçoive jamais le bilan d'athlètes qui ne sont pas les siens.
        Map<UUID, List<Athlete>> byCoach = new LinkedHashMap<>();
        Map<UUID, User> coaches = new HashMap<>();
        for (Athlete a : athletes) {
            notificationService.referentCoach(a.getId(), clubId).ifPresent(coach -> {
                byCoach.computeIfAbsent(coach.getId(), k -> new ArrayList<>()).add(a);
                coaches.putIfAbsent(coach.getId(), coach);
            });
        }
        if (byCoach.isEmpty()) {
            return 0;
        }

        // Les athlètes en vigilance, calculés une seule fois pour tout le club : la détection vit
        // dans le tableau de bord, ce planificateur ne la refait pas. On garde l'ensemble des
        // athlètes concernés et non le nombre d'alertes — un athlète peut en porter trois à la
        // fois (douleur, charge, silence), et annoncer « 3 à surveiller » là où il n'y a qu'une
        // personne rendrait le chiffre inutilisable.
        Set<UUID> watched = dashboardService.alerts(clubId, "all", null).stream()
                .map(a -> a.athleteId())
                .collect(java.util.stream.Collectors.toSet());

        int sent = 0;
        for (Map.Entry<UUID, List<Athlete>> entry : byCoach.entrySet()) {
            int toWatch = (int) entry.getValue().stream()
                    .filter(a -> watched.contains(a.getId())).count();
            WeeklyRecapService.CoachRecap recap =
                    recapService.forCoach(entry.getValue(), from, to, toWatch);
            if (recap.isEmpty()) {
                continue;
            }
            notificationService.notifyCoachWeeklyRecap(coaches.get(entry.getKey()), recap);
            sent++;
        }
        return sent;
    }

    private List<UUID> clubIds() {
        return clubRepository.findAll().stream().map(Club::getId).toList();
    }
}
