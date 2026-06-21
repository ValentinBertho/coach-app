package com.coachrun.service;

import com.coachrun.entity.Activity;
import com.coachrun.entity.Athlete;
import com.coachrun.entity.Club;
import com.coachrun.entity.RaceObjective;
import com.coachrun.entity.User;
import com.coachrun.entity.Workout;
import com.coachrun.entity.WorkoutStep;
import com.coachrun.entity.enums.ActivitySource;
import com.coachrun.entity.enums.ActivityStatus;
import com.coachrun.entity.enums.AthleteLevel;
import com.coachrun.entity.enums.AthleteStatus;
import com.coachrun.entity.enums.ClubStatus;
import com.coachrun.entity.enums.IntensityZone;
import com.coachrun.entity.enums.Sex;
import com.coachrun.entity.enums.UserRole;
import com.coachrun.entity.enums.UserStatus;
import com.coachrun.entity.enums.WorkoutStatus;
import com.coachrun.entity.enums.WorkoutStepType;
import com.coachrun.entity.enums.WorkoutType;
import com.coachrun.entity.enums.RaceObjectiveStatus;
import com.coachrun.entity.enums.RacePriority;
import com.coachrun.repository.ActivityRepository;
import com.coachrun.repository.AthleteRepository;
import com.coachrun.repository.ClubRepository;
import com.coachrun.repository.RaceObjectiveRepository;
import com.coachrun.repository.UserRepository;
import com.coachrun.repository.WorkoutRepository;
import com.coachrun.util.SlugUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * Jeu de données de démonstration (réaliste, en français). Réutilisé au démarrage
 * (profil dev) et par la RAZ. Idempotent : ne régénère pas si l'admin existe déjà.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DemoSeedService {

    public static final String ADMIN_EMAIL = "admin@coachrun.fr";
    public static final String HEAD_COACH_EMAIL = "demo@coachrun.fr";
    public static final String COACH_EMAIL = "coach@coachrun.fr";
    public static final String ATHLETE_EMAIL = "athlete@coachrun.fr";
    public static final String DEMO_PASSWORD = "password123";

    private static final String[] FIRST_M = {
            "Lucas", "Hugo", "Nathan", "Théo", "Antoine", "Maxime", "Julien", "Clément",
            "Romain", "Pierre", "Thomas", "Alexandre", "Mathis", "Gabriel", "Raphaël"};
    private static final String[] FIRST_F = {
            "Emma", "Léa", "Chloé", "Manon", "Camille", "Sarah", "Julie", "Laura",
            "Marie", "Pauline", "Inès", "Clara", "Lucie", "Anaïs", "Justine"};
    private static final String[] LAST = {
            "Martin", "Bernard", "Dubois", "Durand", "Moreau", "Laurent", "Simon", "Michel",
            "Lefebvre", "Garcia", "Roux", "Fontaine", "Rousseau", "Girard", "Bonnet",
            "Dupont", "Lambert", "Fournier", "Mercier", "Blanc"};
    private static final String[] CLUBS = {
            "Running Club Lyon", "Trail Académie Annecy", "Marathon Team Paris"};

    private final ClubRepository clubRepository;
    private final UserRepository userRepository;
    private final AthleteRepository athleteRepository;
    private final WorkoutRepository workoutRepository;
    private final ActivityRepository activityRepository;
    private final RaceObjectiveRepository raceRepository;
    private final PasswordEncoder passwordEncoder;
    private final JdbcTemplate jdbcTemplate;

    private final Random random = new Random(42);

    public boolean isSeeded() {
        return userRepository.existsByEmailIgnoreCase(ADMIN_EMAIL);
    }

    /** Idempotent : ne fait rien si déjà seedé. Retourne true si des données ont été créées. */
    @Transactional
    public boolean seed() {
        if (isSeeded()) {
            return false;
        }
        // Administrateur plateforme (sans club)
        userRepository.save(account(ADMIN_EMAIL, "Admin Plateforme", UserRole.PLATFORM_ADMIN, null, null));

        for (int c = 0; c < CLUBS.length; c++) {
            Club club = newClub(CLUBS[c]);
            seedClub(club, c == 0);
        }
        log.info("[seed démo] Jeu de données de démonstration généré.");
        return true;
    }

    /** Purge toutes les données applicatives puis recharge le jeu de démo. */
    @Transactional
    public void reset() {
        purge();
        // seed() court-circuite si isSeeded() ; après purge, plus d'admin → régénère.
        seed();
        log.warn("[RAZ démo] Données purgées et jeu de démo rechargé.");
    }

    @Transactional
    public void purge() {
        activityRepository.deleteAllInBatch();
        workoutRepository.deleteAllInBatch();   // workout_steps supprimés par cascade FK
        userRepository.deleteAllInBatch();
        athleteRepository.deleteAllInBatch();
        clubRepository.deleteAllInBatch();
    }

    // ----------------------------------------------------------------------

    private void seedClub(Club club, boolean isPrimary) {
        // Coachs
        if (isPrimary) {
            userRepository.save(account(HEAD_COACH_EMAIL, "Coach Démo", UserRole.HEAD_COACH, club, null));
            userRepository.save(account(COACH_EMAIL, "Assistant Coach", UserRole.COACH, club, null));
        } else {
            String slug = club.getSlug();
            userRepository.save(account("head-" + slug + "@coachrun.fr", randomName(true), UserRole.HEAD_COACH, club, null));
            userRepository.save(account("coach-" + slug + "@coachrun.fr", randomName(true), UserRole.COACH, club, null));
        }

        int count = 10 + random.nextInt(6); // 10-15 athlètes
        List<Athlete> athletes = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            athletes.add(athleteRepository.save(newAthlete(club, i)));
        }

        // États d'invitation variés
        Athlete invited = athletes.get(0);
        invited.setInviteToken("DEMO-" + UUID.randomUUID());
        invited.setInviteExpiresAt(Instant.now().plus(14, ChronoUnit.DAYS));

        // Compte athlète connectable (uniquement sur le club principal)
        if (isPrimary) {
            Athlete demoAthlete = athletes.get(1);
            demoAthlete.setEmail(ATHLETE_EMAIL);
            User athleteUser = account(ATHLETE_EMAIL,
                    demoAthlete.getFirstName() + " " + demoAthlete.getLastName(),
                    UserRole.ATHLETE, club, demoAthlete);
            userRepository.save(athleteUser);
            seedTraining(club, demoAthlete);
            seedTraining(club, athletes.get(2));
            seedRace(club, demoAthlete, "Marathon de Paris", 42195, 42);
            seedRace(club, athletes.get(2), "Semi de Lyon", 21097, 70);
        }

        // Dates d'inscription échelonnées (createdAt non modifiable via JPA → SQL)
        for (Athlete a : athletes) {
            backdate(a.getId(), random.nextInt(330) + 5);
        }
    }

    private void seedTraining(Club club, Athlete athlete) {
        LocalDate monday = LocalDate.now().with(java.time.DayOfWeek.MONDAY).minusWeeks(1);
        WorkoutType[] week = {
                WorkoutType.ENDURANCE, WorkoutType.INTERVALS, WorkoutType.RECOVERY,
                WorkoutType.TEMPO, WorkoutType.REST, WorkoutType.LONG_RUN, WorkoutType.RECOVERY};
        for (int d = 0; d < 12; d++) {
            LocalDate date = monday.plusDays(d);
            WorkoutType type = week[d % 7];
            if (type == WorkoutType.REST) {
                continue;
            }
            Workout w = new Workout();
            w.setClub(club);
            w.setAthlete(athlete);
            w.setScheduledDate(date);
            w.setType(type);
            w.setTitle(titleFor(type));
            w.setTargetDistanceM(distanceFor(type));
            w.setTargetDurationS(distanceFor(type) / 3); // ~allure indicative
            w.setStatus(date.isBefore(LocalDate.now())
                    ? (random.nextInt(5) == 0 ? WorkoutStatus.MISSED : WorkoutStatus.COMPLETED)
                    : WorkoutStatus.PLANNED);
            if (w.getStatus() == WorkoutStatus.COMPLETED) {
                w.setRpe(4 + random.nextInt(5));
            }
            w.replaceSteps(stepsFor(type));
            workoutRepository.save(w);

            if (w.getStatus() == WorkoutStatus.COMPLETED && random.nextBoolean()) {
                activityRepository.save(matchedActivity(club, athlete, w));
            }
        }
        // une activité non rattachée
        Activity orphan = baseActivity(club, athlete, LocalDate.now().minusDays(3));
        orphan.setStatus(ActivityStatus.UNMATCHED);
        orphan.setTitle("Sortie libre");
        activityRepository.save(orphan);
    }

    private void seedRace(Club club, Athlete athlete, String name, int distanceM, int daysAhead) {
        RaceObjective race = new RaceObjective();
        race.setClub(club);
        race.setAthlete(athlete);
        race.setName(name);
        race.setDistanceM(distanceM);
        race.setRaceDate(LocalDate.now().plusDays(daysAhead));
        race.setPriority(RacePriority.A);
        race.setStatus(RaceObjectiveStatus.UPCOMING);
        raceRepository.save(race);
    }

    private Club newClub(String name) {
        Club club = new Club();
        club.setName(name);
        club.setSlug(uniqueSlug(name));
        club.setStatus(ClubStatus.ACTIVE);
        return clubRepository.save(club);
    }

    private User account(String email, String fullName, UserRole role, Club club, Athlete athlete) {
        User u = new User();
        u.setEmail(email);
        u.setFullName(fullName);
        u.setRole(role);
        u.setStatus(UserStatus.ACTIVE);
        u.setPasswordHash(passwordEncoder.encode(DEMO_PASSWORD));
        u.setClub(club);
        u.setAthlete(athlete);
        return u;
    }

    private Athlete newAthlete(Club club, int index) {
        boolean male = random.nextBoolean();
        Athlete a = new Athlete();
        a.setClub(club);
        a.setFirstName(male ? pick(FIRST_M) : pick(FIRST_F));
        a.setLastName(pick(LAST));
        a.setSex(male ? Sex.MALE : Sex.FEMALE);
        a.setLevel(AthleteLevel.values()[random.nextInt(AthleteLevel.values().length)]);
        a.setStatus(switch (random.nextInt(8)) {
            case 0 -> AthleteStatus.PAUSED;
            case 1 -> AthleteStatus.ARCHIVED;
            default -> AthleteStatus.ACTIVE;
        });
        if (index % 4 != 0) { // ~75 % avec email
            a.setEmail(a.getFirstName().toLowerCase() + "." + a.getLastName().toLowerCase()
                    + index + "@example.fr");
        }
        a.setHrMax(180 + random.nextInt(25));
        a.setHrRest(45 + random.nextInt(15));
        a.setVma(BigDecimal.valueOf(13 + random.nextInt(7) + random.nextInt(10) / 10.0));
        a.setWeightKg(BigDecimal.valueOf(52 + random.nextInt(35)));
        return a;
    }

    private Activity matchedActivity(Club club, Athlete athlete, Workout w) {
        Activity act = baseActivity(club, athlete, w.getScheduledDate());
        act.setStatus(ActivityStatus.MATCHED);
        act.setMatchedWorkoutId(w.getId());
        act.setTitle(w.getTitle());
        int target = w.getTargetDistanceM() == null ? 8000 : w.getTargetDistanceM();
        act.setDistanceM(target + random.nextInt(800) - 400);
        return act;
    }

    private Activity baseActivity(Club club, Athlete athlete, LocalDate date) {
        Activity act = new Activity();
        act.setClub(club);
        act.setAthlete(athlete);
        act.setSource(ActivitySource.STRAVA);
        act.setExternalId("seed-" + UUID.randomUUID());
        act.setActivityDate(date);
        act.setDistanceM(6000 + random.nextInt(9000));
        act.setDurationS(1800 + random.nextInt(3600));
        act.setAvgHr(135 + random.nextInt(40));
        act.setElevationGainM(random.nextInt(400));
        act.setStatus(ActivityStatus.IMPORTED);
        return act;
    }

    private List<WorkoutStep> stepsFor(WorkoutType type) {
        List<WorkoutStep> steps = new ArrayList<>();
        steps.add(step(WorkoutStepType.WARMUP, 1, IntensityZone.Z2, null, 900));
        if (type == WorkoutType.INTERVALS) {
            steps.add(step(WorkoutStepType.REPETITION, 8, IntensityZone.Z5, 400, null));
            steps.add(step(WorkoutStepType.RECOVERY, 8, IntensityZone.Z1, 200, null));
        } else if (type == WorkoutType.TEMPO) {
            steps.add(step(WorkoutStepType.STEADY, 1, IntensityZone.Z3, 5000, null));
        } else {
            steps.add(step(WorkoutStepType.STEADY, 1, IntensityZone.Z2, distanceFor(type), null));
        }
        steps.add(step(WorkoutStepType.COOLDOWN, 1, IntensityZone.Z1, null, 600));
        return steps;
    }

    private WorkoutStep step(WorkoutStepType t, int rep, IntensityZone z, Integer dist, Integer dur) {
        WorkoutStep s = new WorkoutStep();
        s.setStepType(t);
        s.setRepetitions(rep);
        s.setZone(z);
        s.setDistanceM(dist);
        s.setDurationS(dur);
        return s;
    }

    private String titleFor(WorkoutType type) {
        return switch (type) {
            case INTERVALS -> "VMA 8×400m";
            case TEMPO -> "Tempo 5 km au seuil";
            case LONG_RUN -> "Sortie longue";
            case RECOVERY -> "Footing récupération";
            default -> "Endurance fondamentale";
        };
    }

    private int distanceFor(WorkoutType type) {
        return switch (type) {
            case LONG_RUN -> 18000;
            case INTERVALS -> 9000;
            case TEMPO -> 10000;
            case RECOVERY -> 6000;
            default -> 12000;
        };
    }

    private void backdate(UUID athleteId, int daysAgo) {
        jdbcTemplate.update("UPDATE athletes SET created_at = ? WHERE id = ?",
                java.sql.Timestamp.from(Instant.now().minus(daysAgo, ChronoUnit.DAYS)), athleteId);
    }

    private String uniqueSlug(String name) {
        String base = SlugUtil.slugify(name);
        String slug = base;
        int i = 1;
        while (clubRepository.existsBySlug(slug)) {
            slug = base + "-" + (++i);
        }
        return slug;
    }

    private String randomName(boolean male) {
        return (male ? pick(FIRST_M) : pick(FIRST_F)) + " " + pick(LAST);
    }

    private String pick(String[] arr) {
        return arr[random.nextInt(arr.length)];
    }
}
