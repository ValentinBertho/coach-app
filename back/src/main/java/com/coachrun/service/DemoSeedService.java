package com.coachrun.service;

import com.coachrun.entity.Activity;
import com.coachrun.entity.Athlete;
import com.coachrun.entity.Club;
import com.coachrun.entity.RaceObjective;
import com.coachrun.entity.TrainingPlan;
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
import com.coachrun.entity.enums.Discipline;
import com.coachrun.entity.enums.RunDrillCategory;
import com.coachrun.entity.enums.EquipmentType;
import com.coachrun.entity.enums.ExerciseCategory;
import com.coachrun.entity.enums.MuscleGroup;
import com.coachrun.entity.enums.RunDistance;
import com.coachrun.entity.enums.TestType;
import com.coachrun.entity.Athlete1rmProfile;
import com.coachrun.entity.EstimatedOneRm;
import com.coachrun.entity.StrengthLoadTracking;
import com.coachrun.entity.PpExercise;
import com.coachrun.entity.StrengthSession;
import com.coachrun.entity.WorkoutTemplate;
import com.coachrun.entity.enums.RmFormula;
import com.coachrun.dto.request.LactateTestRequest;
import com.coachrun.dto.request.LactateTestStepRequest;
import com.coachrun.dto.request.PerformanceRequest;
import com.coachrun.dto.request.PhysioProfileRequest;
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
    private final com.coachrun.repository.AthleteUnavailabilityRepository unavailabilityRepository;
    private final com.coachrun.repository.TrainingGroupRepository groupRepository;
    private final com.coachrun.repository.WorkoutTemplateRepository templateRepository;
    private final com.coachrun.repository.RunDrillRepository runDrillRepository;
    private final com.coachrun.repository.MessageRepository messageRepository;
    private final com.coachrun.repository.PushSubscriptionRepository pushSubscriptionRepository;
    private final com.coachrun.repository.TrainingPlanRepository planRepository;
    private final AthletePhysioService physioService;
    private final LactateTestService lactateTestService;
    private final StrengthScheduleService strengthScheduleService;
    private final com.coachrun.repository.PpExerciseRepository exerciseRepository;
    private final com.coachrun.repository.Athlete1rmProfileRepository profile1rmRepository;
    private final com.coachrun.repository.StrengthSessionRepository strengthSessionRepository;
    private final com.coachrun.repository.EstimatedOneRmRepository estimatedRepository;
    private final com.coachrun.repository.StrengthTestRepository strengthTestRepository;
    private final com.coachrun.repository.StrengthLoadTrackingRepository strengthLoadRepository;
    private final com.coachrun.repository.ClubMemberRepository clubMemberRepository;
    private final com.coachrun.repository.CoachAthleteRelationRepository relationRepository;
    private final com.coachrun.repository.AthleteCoachPermissionRepository permissionRepository;
    private final PasswordEncoder passwordEncoder;
    private final JdbcTemplate jdbcTemplate;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

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
        // Réinitialise la séquence pseudo-aléatoire : le service étant un singleton, chaque appel
        // à seed() doit produire un jeu de données identique (déterminisme inter-tests).
        random.setSeed(42);
        // Administrateur plateforme (sans club)
        userRepository.save(account(ADMIN_EMAIL, "Admin Plateforme", UserRole.PLATFORM_ADMIN, null, null));

        for (int c = 0; c < CLUBS.length; c++) {
            Club club = newClub(CLUBS[c]);
            seedClub(club, c == 0);
        }
        seedRelations();
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
        pushSubscriptionRepository.deleteAllInBatch();
        messageRepository.deleteAllInBatch();
        activityRepository.deleteAllInBatch();
        workoutRepository.deleteAllInBatch();   // workout_steps supprimés par cascade FK
        planRepository.deleteAllInBatch();      // training_plan_athletes supprimés par cascade FK
        userRepository.deleteAllInBatch();
        athleteRepository.deleteAllInBatch();    // détache d'abord les athlètes des groupes
        groupRepository.deleteAllInBatch();
        templateRepository.deleteAllInBatch();
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

        // Groupe d'entraînement + affectation de quelques athlètes
        com.coachrun.entity.TrainingGroup group = new com.coachrun.entity.TrainingGroup();
        group.setClub(club);
        group.setName(isPrimary ? "Marathon" : "Groupe principal");
        group = groupRepository.save(group);
        for (int i = 0; i < Math.min(4, athletes.size()); i++) {
            athletes.get(i).setGroup(group);
        }

        // Éducatifs de course (gammes) — réutilisés dans les structures de séance.
        UUID warmupDrillId = seedRunDrills(club);

        // Modèles de séances (bibliothèque) — structure DARI Lab en fourchettes (modèle unique).
        WorkoutTemplate endurance = seedTemplate(club, "Endurance fondamentale", WorkoutType.ENDURANCE, "Footing Z2", 12000);
        WorkoutTemplate vma = seedTemplate(club, "VMA 10x400", WorkoutType.INTERVALS, "VMA 10×400m", 9000);
        seedCourseStructure(endurance, warmupDrillId);
        seedCourseStructure(vma, warmupDrillId);
        if (isPrimary) {
            WorkoutTemplate longRun = seedTemplate(club, "Sortie longue", WorkoutType.LONG_RUN, "Sortie longue", 20000);
            seedCourseStructure(longRun, warmupDrillId);
        }

        // Compte athlète connectable (uniquement sur le club principal)
        if (isPrimary) {
            Athlete demoAthlete = athletes.get(1);
            demoAthlete.setEmail(ATHLETE_EMAIL);
            User athleteUser = account(ATHLETE_EMAIL,
                    demoAthlete.getFirstName() + " " + demoAthlete.getLastName(),
                    UserRole.ATHLETE, club, demoAthlete);
            athleteUser = userRepository.save(athleteUser);
            User headCoach = userRepository.findByEmailIgnoreCase(HEAD_COACH_EMAIL).orElse(null);
            // États de forme variés pour une démo réaliste du cockpit (vert / orange / rouge).
            seedTraining(club, demoAthlete, 4, 0);                  // sain
            seedTraining(club, athletes.get(2), 9, 1);             // fatigue élevée + charge → rouge
            seedTraining(club, athletes.get(3), 6, 5);             // douleur signalée → alerte
            seedTraining(club, athletes.get(4), 5, 0);             // à surveiller (orange)
            seedTraining(club, athletes.get(5), 2, 0);             // vert
            if (athletes.size() > 6) {
                seedPlannedWeek(club, athletes.get(6));            // silencieux → alerte de suivi
            }
            seedRace(club, demoAthlete, "Marathon de Paris", 42195, 42);
            seedRace(club, athletes.get(2), "Semi de Lyon", 21097, 70);
            seedUnavailability(club, athletes.get(3), com.coachrun.entity.enums.UnavailabilityReason.INJURY,
                    -3, 10, "Entorse cheville — reprise progressive");
            if (headCoach != null) {
                seedMessage(club, demoAthlete, headCoach, "Bravo pour ta semaine, on garde le rythme ! 💪");
                seedMessage(club, demoAthlete, athleteUser, "Merci coach, je me sens en forme.");
            }
            // Données DARI Lab : physiologie (VDOT, seuils), test lactate, préparation physique.
            seedPhysio(club, athletes, demoAthlete);
            seedPreparationPhysique(club, demoAthlete);
            seedClubMembership(club, athletes, demoAthlete);
        }

        // Dates d'inscription échelonnées (createdAt non modifiable via JPA → SQL)
        for (Athlete a : athletes) {
            backdate(a.getId(), random.nextInt(330) + 5);
        }
    }

    /** Profil physiologique DARI Lab : discipline, seuils, performances (→ VDOT) et test lactate. */
    private void seedPhysio(Club club, List<Athlete> athletes, Athlete demoAthlete) {
        int n = Math.min(6, athletes.size());
        for (int i = 0; i < n; i++) {
            Athlete a = athletes.get(i);
            Discipline discipline = (i % 3 == 0) ? Discipline.TRAIL : Discipline.ROUTE;
            physioService.updateProfile(club.getId(), a.getId(), new PhysioProfileRequest(
                    discipline,
                    BigDecimal.valueOf(3.3 + i * 0.05), BigDecimal.valueOf(3.7 + i * 0.05),
                    BigDecimal.valueOf(4.0 + i * 0.05),
                    185 - i, 150, 168, null, null, null, null));
            int fiveK = 1080 + i * 35;                                  // 18:00 et plus
            int tenK = (int) Math.round(fiveK * Math.pow(2, 1.06));     // équivalence Riegel
            physioService.addPerformance(club.getId(), a.getId(),
                    new PerformanceRequest(RunDistance.D5KM, fiveK, LocalDate.now().minusDays(40 + i)));
            physioService.addPerformance(club.getId(), a.getId(),
                    new PerformanceRequest(RunDistance.D10KM, tenK, LocalDate.now().minusDays(20 + i)));
        }
        // Test lactate complet pour l'athlète démo.
        lactateTestService.create(club.getId(), demoAthlete.getId(), new LactateTestRequest(
                TestType.LACTATE, LocalDate.now().minusDays(30), "Test de terrain sur piste",
                BigDecimal.valueOf(0.8), 60, 188, true, List.of(
                lactateStep(3.0, 130, 1.0), lactateStep(3.3, 140, 1.2), lactateStep(3.6, 150, 1.8),
                lactateStep(3.9, 160, 3.0), lactateStep(4.2, 170, 5.5), lactateStep(4.5, 178, 8.0))));
    }

    private LactateTestStepRequest lactateStep(double speedMs, int hr, double lactate) {
        return new LactateTestStepRequest(BigDecimal.valueOf(speedMs), hr, BigDecimal.valueOf(lactate), null, 180);
    }

    /** Bibliothèque d'exercices de force + séance structurée + 1RM + historique e1RM. */
    private void seedPreparationPhysique(Club club, Athlete demoAthlete) {
        PpExercise squat = newExercise(club, "Squat barre", ExerciseCategory.FORCE_MAX,
                MuscleGroup.QUADRICEPS, EquipmentType.BARRE);
        newExercise(club, "Soulevé de terre", ExerciseCategory.FORCE_MAX, MuscleGroup.ISCHIOS, EquipmentType.BARRE);
        PpExercise gainage = newExercise(club, "Gainage planche", ExerciseCategory.GAINAGE, MuscleGroup.TRONC, EquipmentType.POIDS_DU_CORPS);
        PpExercise fentes = newExercise(club, "Fentes haltères", ExerciseCategory.PUISSANCE, MuscleGroup.FESSIERS, EquipmentType.HALTERES);

        Athlete1rmProfile rm = new Athlete1rmProfile();
        rm.setAthlete(demoAthlete);
        rm.setExerciseId(squat.getId());
        rm.setRmKg(BigDecimal.valueOf(120));
        rm.setSource("tested");
        profile1rmRepository.save(rm);

        // Séance de force structurée multi-blocs : activation (circuit) → principal (squat) → accessoire (iso).
        StrengthSession session = new StrengthSession();
        session.setClub(club);
        session.setName("Force max bas du corps");
        session.setFavorite(true);
        session.setStructureJson(("""
                {"blocks":[
                  {"id":"b0","blockType":"ACTIVATION","format":"CIRCUIT","rounds":3,"workSec":40,"restSec":20,"exercises":[
                    {"exerciseId":"%s","exerciseName":"Fentes haltères","setType":"STANDARD",
                     "prescription":{"chargeRefType":"KG_FIXE","chargeKgMin":12,
                                     "effortRefType":"RPE","rpeMin":7,"sets":3,"repsFixed":10,"restSecMin":20,"restSecMax":20}}]},
                  {"id":"b1","blockType":"PRINCIPAL","format":"CLASSIQUE","exercises":[
                    {"exerciseId":"%s","exerciseName":"Squat barre","setType":"STANDARD",
                     "prescription":{"chargeRefType":"PCT_RM_RANGE","chargePctRmMin":80,"chargePctRmMax":85,
                                     "effortRefType":"RIR_RANGE","rirMin":1,"rirMax":3,"sets":4,"repsFixed":5,
                                     "tempo":"3-1-X-1","restSecMin":120,"restSecMax":180}}]},
                  {"id":"b2","blockType":"ACCESSOIRE","format":"ISOMETRIE","exercises":[
                    {"exerciseId":"%s","exerciseName":"Gainage planche","setType":"ISO_YIELDING",
                     "prescription":{"effortRefType":"RPE","rpeMin":8,"sets":3,"durationSec":45,"restSecMin":45,"restSecMax":60}}]}]}""")
                .formatted(fentes.getId(), squat.getId(), gainage.getId()));
        session = strengthSessionRepository.save(session);

        // Assignation de la séance de force au calendrier de l'athlète démo (cette semaine).
        strengthScheduleService.schedule(club.getId(), demoAthlete.getId(), session.getId(),
                LocalDate.now().plusDays(2), com.coachrun.entity.enums.FieldsPreset.AVANCE);

        // Historique e1RM (courbe de progression) sur le Squat.
        int[] daysAgo = {70, 45, 20, 5};
        double[] values = {110.0, 113.5, 116.0, 118.5};
        for (int i = 0; i < daysAgo.length; i++) {
            EstimatedOneRm h = new EstimatedOneRm();
            h.setAthlete(demoAthlete);
            h.setExerciseId(squat.getId());
            h.setChargeKg(BigDecimal.valueOf(100));
            h.setReps(5);
            h.setRpeOrRir("RIR2");
            h.setFormulaUsed(RmFormula.NUZZO);
            h.setE1rmKg(BigDecimal.valueOf(values[i]));
            h = estimatedRepository.save(h);
            jdbcTemplate.update("update estimated_1rm set created_at = ? where id = ?",
                    java.sql.Timestamp.from(Instant.now().minus(daysAgo[i], ChronoUnit.DAYS)), h.getId());
        }

        // Tests de force datés (protocoles DARI Lab) sur le Squat.
        seedStrengthTest(demoAthlete, squat.getId(),
                com.coachrun.entity.enums.StrengthTestProtocol.REP_TEST_3_5, 100, 5, 70, 116.0);
        seedStrengthTest(demoAthlete, squat.getId(),
                com.coachrun.entity.enums.StrengthTestProtocol.TRUE_1RM, 120, 1, 5, 120.0);

        // Suivi de charge interne (UA méca/métab) sur les dernières séances de force.
        int[] loadDaysAgo = {28, 21, 14, 7, 2};
        double[] meca = {1850, 2100, 1980, 2240, 2050};
        double[] metab = {360, 400, 380, 420, 390};
        for (int i = 0; i < loadDaysAgo.length; i++) {
            StrengthLoadTracking load = new StrengthLoadTracking();
            load.setAthlete(demoAthlete);
            load.setSessionDate(LocalDate.now().minusDays(loadDaysAgo[i]));
            load.setMechanicalLoad(BigDecimal.valueOf(meca[i]));
            load.setMetabolicLoad(BigDecimal.valueOf(metab[i]));
            strengthLoadRepository.save(load);
        }
    }

    private void seedStrengthTest(Athlete athlete, java.util.UUID exerciseId,
                                  com.coachrun.entity.enums.StrengthTestProtocol protocol,
                                  double weightKg, int reps, int daysAgo, double e1rm) {
        com.coachrun.entity.StrengthTest t = new com.coachrun.entity.StrengthTest();
        t.setAthlete(athlete);
        t.setExerciseId(exerciseId);
        t.setProtocol(protocol);
        t.setTestDate(LocalDate.now().minusDays(daysAgo));
        t.setWeightKg(BigDecimal.valueOf(weightKg));
        t.setReps(reps);
        t.setComputedE1rmKg(BigDecimal.valueOf(e1rm));
        strengthTestRepository.save(t);
    }

    /** Multi-coach DARI Lab : rôles club, coach référent, statut privé/club, permission accordée. */
    private void seedClubMembership(Club club, List<Athlete> athletes, Athlete demoAthlete) {
        User owner = userRepository.findByEmailIgnoreCase(HEAD_COACH_EMAIL).orElse(null);
        User assistant = userRepository.findByEmailIgnoreCase(COACH_EMAIL).orElse(null);
        if (owner == null || assistant == null) {
            return;
        }
        clubMemberRepository.save(member(club, owner, com.coachrun.entity.enums.ClubRole.OWNER));
        clubMemberRepository.save(member(club, assistant, com.coachrun.entity.enums.ClubRole.COACH_ASSISTANT));

        // Coach référent = owner ; les 2 derniers athlètes sont privés, le reste rattaché au club.
        for (int i = 0; i < athletes.size(); i++) {
            Athlete a = athletes.get(i);
            com.coachrun.entity.CoachAthleteRelation r = new com.coachrun.entity.CoachAthleteRelation();
            r.setCoach(owner);
            r.setAthlete(a);
            r.setClub(i >= athletes.size() - 2 ? null : club);
            r.setReferent(true);
            relationRepository.save(r);
        }

        // L'assistant reçoit une permission "lecture" sur l'athlète démo (athlète club).
        com.coachrun.entity.AthleteCoachPermission perm = new com.coachrun.entity.AthleteCoachPermission();
        perm.setAthlete(demoAthlete);
        perm.setCoach(assistant);
        perm.setPermission(com.coachrun.entity.enums.PermissionLevel.READ);
        perm.setGrantedBy(owner);
        permissionRepository.save(perm);
    }

    private com.coachrun.entity.ClubMember member(Club club, User coach, com.coachrun.entity.enums.ClubRole role) {
        com.coachrun.entity.ClubMember m = new com.coachrun.entity.ClubMember();
        m.setClub(club);
        m.setCoach(coach);
        m.setClubRole(role);
        return m;
    }

    /**
     * Éducatifs de course (gammes technique/amplitude) du club, distincts du renforcement (CDC §9).
     * Renvoie l'id d'un éducatif d'échauffement, à attacher au bloc d'échauffement des séances.
     */
    private UUID seedRunDrills(Club club) {
        UUID montees = newRunDrill(club, "Montées de genoux", RunDrillCategory.TECHNIQUE,
                "Genoux hauts, gainage actif, contact pied dynamique.").getId();
        newRunDrill(club, "Talons-fesses", RunDrillCategory.TECHNIQUE,
                "Ramener le talon sous la fesse, buste droit.");
        newRunDrill(club, "Foulées bondissantes", RunDrillCategory.AMPLITUDE,
                "Grandes foulées, poussée complète, suspension.");
        newRunDrill(club, "Pas chassés", RunDrillCategory.AMPLITUDE,
                "Travail latéral, hanches mobiles.");
        return montees;
    }

    private com.coachrun.entity.RunDrill newRunDrill(Club club, String name, RunDrillCategory category,
                                                     String description) {
        com.coachrun.entity.RunDrill d = new com.coachrun.entity.RunDrill();
        d.setClub(club);
        d.setName(name);
        d.setCategory(category);
        d.setDescription(description);
        d.setVideoUrl("https://www.youtube.com/results?search_query=" + name.replace(' ', '+') + "+course");
        return runDrillRepository.save(d);
    }

    /**
     * Structure DARI Lab (prescription en fourchettes) attachée à un modèle de séance course, adaptée
     * au type, avec un éducatif de course attaché à l'échauffement (intégration éducatifs ↔ séance).
     */
    private void seedCourseStructure(WorkoutTemplate t, UUID warmupDrillId) {
        t.setDiscipline(Discipline.ROUTE);
        String warmup = """
                {"id":"wu1","type":"warmup","durationS":900,
                 "prescription":{"ref":"PCT_LT1","minPct":75,"maxPct":88},
                 "drillIds":["%s"]}""".formatted(warmupDrillId);
        String main = switch (t.getType()) {
            case INTERVALS -> """
                {"id":"m1","type":"intervals","reps":10,"distanceM":400,
                 "prescription":{"ref":"PCT_PACE_5KM","minPct":100,"maxPct":108},
                 "recovery":{"type":"jog","durationS":60,
                             "prescription":{"ref":"PCT_LT1","minPct":60,"maxPct":75}}}""";
            case LONG_RUN -> """
                {"id":"m1","type":"long","distanceM":18000,
                 "prescription":{"ref":"PCT_LT1","minPct":70,"maxPct":82}}""";
            default -> """
                {"id":"m1","type":"easy","durationS":2400,
                 "prescription":{"ref":"PCT_LT1","minPct":72,"maxPct":85}}""";
        };
        t.setStructureJson("""
                {"warmup":[%s],
                 "main":[%s],
                 "cooldown":[{"id":"cd1","type":"cooldown","durationS":600,
                              "prescription":{"ref":"PCT_LT1","minPct":60,"maxPct":80}}]}"""
                .formatted(warmup, main));
    }

    private PpExercise newExercise(Club club, String name, ExerciseCategory category,
                                  MuscleGroup muscle, EquipmentType equipment) {
        PpExercise e = new PpExercise();
        e.setClub(club);
        e.setName(name);
        e.setCategory(category);
        e.setMuscleGroups(new java.util.HashSet<>(java.util.Set.of(muscle)));
        e.setEquipment(new java.util.HashSet<>(java.util.Set.of(equipment)));
        return exerciseRepository.save(e);
    }

    /**
     * Démonstration du modèle ouvert (many-to-many) : un athlète a plusieurs coachs,
     * un plan est attribué à plusieurs athlètes, des coachs/athlètes appartiennent à
     * plusieurs clubs.
     */
    private void seedRelations() {
        List<Club> clubs = clubRepository.findAll();
        Club primary = clubs.stream().filter(c -> CLUBS[0].equals(c.getName())).findFirst().orElse(null);
        Club second = clubs.stream().filter(c -> CLUBS[1].equals(c.getName())).findFirst().orElse(null);
        if (primary == null) {
            return;
        }
        User headCoach = userRepository.findByEmailIgnoreCase(HEAD_COACH_EMAIL).orElse(null);
        User assistant = userRepository.findByEmailIgnoreCase(COACH_EMAIL).orElse(null);
        List<Athlete> athletes = athleteRepository.findByClubIdOrderByLastNameAsc(primary.getId());

        // Coach démo multi-club : il intervient aussi dans un second club.
        if (headCoach != null && second != null) {
            headCoach.getAdditionalClubs().add(second);
        }

        // Plusieurs coachs sur un même athlète.
        for (int i = 0; i < Math.min(5, athletes.size()); i++) {
            Athlete a = athletes.get(i);
            if (headCoach != null) {
                a.getCoaches().add(headCoach);
            }
            if (assistant != null && i % 2 == 0) {
                a.getCoaches().add(assistant);
            }
        }

        // Athlète multi-club.
        if (second != null && !athletes.isEmpty()) {
            athletes.get(0).getAdditionalClubs().add(second);
        }

        // Plan attribué à plusieurs athlètes.
        if (!athletes.isEmpty()) {
            TrainingPlan plan = new TrainingPlan();
            plan.setClub(primary);
            plan.setName("Prépa 10 km — 8 semaines");
            plan.setDescription("Plan de démonstration attribué à plusieurs athlètes.");
            plan.setDurationWeeks(8);
            plan.setItemsJson("[]");
            for (int i = 0; i < Math.min(3, athletes.size()); i++) {
                plan.getAthletes().add(athletes.get(i));
            }
            planRepository.save(plan);
        }
    }

    private void seedTraining(Club club, Athlete athlete) {
        seedTraining(club, athlete, 4, 0); // profil sain par défaut
    }

    /**
     * Historique d'entraînement sur 3 semaines (réalisé + retours), avec un état de forme cible
     * ({@code peakFatigue}, {@code peakPain} portés par le dernier retour) — pour une démo réaliste
     * du cockpit coach (pastilles de forme, alertes, charge, analytics).
     */
    private void seedTraining(Club club, Athlete athlete, int peakFatigue, int peakPain) {
        LocalDate monday = LocalDate.now().with(java.time.DayOfWeek.MONDAY).minusWeeks(2);
        WorkoutType[] week = {
                WorkoutType.ENDURANCE, WorkoutType.INTERVALS, WorkoutType.RECOVERY,
                WorkoutType.TEMPO, WorkoutType.REST, WorkoutType.LONG_RUN, WorkoutType.RECOVERY};
        Workout lastCompleted = null;
        for (int d = 0; d < 19; d++) {
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
                    ? (random.nextInt(6) == 0 ? WorkoutStatus.MISSED : WorkoutStatus.COMPLETED)
                    : WorkoutStatus.PLANNED);
            if (w.getStatus() == WorkoutStatus.COMPLETED) {
                w.setRpe(4 + random.nextInt(5));
                w.setFatigue(Math.max(1, Math.min(10, peakFatigue - 2 + random.nextInt(3))));
                w.setPain(peakPain == 0 ? 0 : Math.max(0, Math.min(10, peakPain - 1 + random.nextInt(2))));
            }
            w.replaceSteps(stepsFor(type));
            workoutRepository.save(w);
            if (w.getStatus() == WorkoutStatus.COMPLETED) {
                lastCompleted = w;
                if (random.nextBoolean()) {
                    activityRepository.save(matchedActivity(club, athlete, w));
                }
            }
        }
        // Le dernier retour porte l'état de forme cible (pilote la pastille + les alertes).
        if (lastCompleted != null) {
            lastCompleted.setFatigue(peakFatigue);
            lastCompleted.setPain(peakPain);
        }
        // une activité non rattachée
        Activity orphan = baseActivity(club, athlete, LocalDate.now().minusDays(3));
        orphan.setStatus(ActivityStatus.UNMATCHED);
        orphan.setTitle("Sortie libre");
        activityRepository.save(orphan);
    }

    /** Semaine planifiée sans retour (athlète « silencieux » → alerte de suivi). */
    private void seedPlannedWeek(Club club, Athlete athlete) {
        LocalDate monday = LocalDate.now().with(java.time.DayOfWeek.MONDAY).minusWeeks(1);
        WorkoutType[] week = {WorkoutType.ENDURANCE, WorkoutType.TEMPO, WorkoutType.LONG_RUN};
        for (int i = 0; i < week.length; i++) {
            Workout w = new Workout();
            w.setClub(club);
            w.setAthlete(athlete);
            w.setScheduledDate(monday.plusDays(i * 2L));
            w.setType(week[i]);
            w.setTitle(titleFor(week[i]));
            w.setTargetDistanceM(distanceFor(week[i]));
            w.setTargetDurationS(distanceFor(week[i]) / 3);
            w.setStatus(WorkoutStatus.PLANNED);
            w.replaceSteps(stepsFor(week[i]));
            workoutRepository.save(w);
        }
    }

    private void seedUnavailability(Club club, Athlete athlete,
                                    com.coachrun.entity.enums.UnavailabilityReason reason,
                                    int startOffset, int endOffset, String notes) {
        com.coachrun.entity.AthleteUnavailability u = new com.coachrun.entity.AthleteUnavailability();
        u.setClub(club);
        u.setAthlete(athlete);
        u.setStartDate(LocalDate.now().plusDays(startOffset));
        u.setEndDate(LocalDate.now().plusDays(endOffset));
        u.setReason(reason);
        u.setNotes(notes);
        unavailabilityRepository.save(u);
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

    private WorkoutTemplate seedTemplate(Club club, String name, WorkoutType type, String title, int distanceM) {
        WorkoutTemplate t = new WorkoutTemplate();
        t.setClub(club);
        t.setName(name);
        t.setType(type);
        t.setTitle(title);
        t.setTargetDistanceM(distanceM);
        try {
            t.setStepsJson(objectMapper.writeValueAsString(stepsFor(type).stream().map(s -> {
                java.util.Map<String, Object> m = new java.util.HashMap<>();
                m.put("stepType", s.getStepType().name());
                m.put("repetitions", s.getRepetitions());
                m.put("zone", s.getZone() == null ? null : s.getZone().name());
                m.put("distanceM", s.getDistanceM());
                m.put("durationS", s.getDurationS());
                return m;
            }).toList()));
        } catch (Exception ignored) {
            t.setStepsJson("[]");
        }
        return templateRepository.save(t);
    }

    private void seedMessage(Club club, Athlete athlete, User sender, String body) {
        com.coachrun.entity.Message m = new com.coachrun.entity.Message();
        m.setClub(club);
        m.setAthlete(athlete);
        m.setSenderUserId(sender.getId());
        m.setSenderRole(sender.getRole());
        m.setSenderName(sender.getFullName());
        m.setBody(body);
        messageRepository.save(m);
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
