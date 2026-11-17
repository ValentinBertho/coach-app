package com.coachrun.service;

import com.coachrun.entity.Club;
import com.coachrun.entity.PpExercise;
import com.coachrun.entity.StrengthSession;
import com.coachrun.entity.enums.CategoryDomain;
import com.coachrun.entity.RunDrill;
import com.coachrun.entity.SessionCategory;
import com.coachrun.entity.TrainingZone;
import com.coachrun.entity.WorkoutTemplate;
import com.coachrun.entity.enums.Discipline;
import com.coachrun.entity.enums.RunDrillCategory;
import com.coachrun.entity.enums.WorkoutType;
import com.coachrun.repository.PpExerciseRepository;
import com.coachrun.repository.RunDrillRepository;
import com.coachrun.repository.StrengthSessionRepository;
import com.coachrun.repository.SessionCategoryRepository;
import com.coachrun.repository.TrainingZoneRepository;
import com.coachrun.repository.WorkoutTemplateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Jeu de départ d'un club : ce qu'un coach trouve dans sa bibliothèque le jour de son inscription.
 *
 * <p><b>Le problème.</b> La création de compte posait un club, un utilisateur et une adhésion —
 * rien d'autre. Un coach qui découvrait le produit ouvrait donc une bibliothèque vide, un
 * calendrier vide et aucune catégorie de rangement. Or presque tout ici est scopé par club
 * ({@code SessionCategory.club}, {@code WorkoutTemplate.club}, {@code RunDrill.club}) et le jeu de
 * démonstration ne tourne qu'en profil {@code dev}. La première impression du produit était donc
 * muette, et la première tâche demandée à un coach qui n'a encore rien vu était d'écrire lui-même
 * les six séances les plus banales de la course à pied.</p>
 *
 * <p><b>Ce qui existait déjà, et qu'on ne refait pas.</b> Les <b>zones d'intensité</b> sont
 * provisionnées paresseusement à la première lecture du club ({@link TrainingZoneSeedService},
 * appelé par {@code TrainingZoneService#list}). Le moteur physiologique n'a donc jamais été muet ;
 * ce service se contente de déclencher ce seed tout de suite, parce qu'il lui faut les zones pour
 * prescrire ses séances.</p>
 *
 * <p><b>Ce que ce n'est pas.</b> Ni un carcan ni une démo : tout est modifiable et supprimable,
 * rien n'est marqué comme spécial, et aucune donnée d'athlète n'est inventée. Un coach qui préfère
 * sa propre bibliothèque supprime six lignes et n'y pense plus — c'est moins de travail que d'en
 * écrire dix.</p>
 *
 * <p><b>Idempotent</b> : n'agit que sur un club dont la bibliothèque est vide, donc rejouable sans
 * risque de doublon sur un club déjà installé.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ClubStarterKitService {

    private final SessionCategoryRepository categoryRepository;
    private final WorkoutTemplateRepository templateRepository;
    private final RunDrillRepository drillRepository;
    private final TrainingZoneRepository zoneRepository;
    private final TrainingZoneSeedService zoneSeedService;
    private final ZoneSetService zoneSetService;
    private final com.coachrun.repository.ClubRepository clubRepository;
    private final PpCatalogSeedService ppCatalogSeedService;
    private final PpExerciseRepository exerciseRepository;
    private final StrengthSessionRepository strengthSessionRepository;

    /**
     * Installe le jeu de départ si la bibliothèque du club est vide.
     *
     * <p>Prend un identifiant et non une entité : l'appel se fait après le commit de
     * l'inscription, où le club chargé par la transaction précédente est détaché.</p>
     *
     * <p><b>{@code REQUIRES_NEW}, et ce n'est pas un détail.</b> L'appel arrive depuis un
     * {@code afterCommit} : la synchronisation de la transaction d'inscription y est encore
     * active, si bien qu'une propagation {@code REQUIRED} rejoint une transaction <i>déjà
     * committée</i> — et toutes les écritures sont perdues, <b>sans la moindre exception</b>. Le
     * défaut est invisible en test unitaire, où la méthode est appelée directement et ouvre sa
     * propre transaction ; il ne s'est vu qu'en inscrivant un vrai coach et en regardant sa
     * bibliothèque rester vide.</p>
     *
     * @return vrai si quelque chose a été posé.
     */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public boolean install(UUID clubId) {
        if (!drillRepository.findByClubIdOrderByCategoryAscNameAsc(clubId).isEmpty()
                || !categoryRepository.findByClubIdAndDomainOrderBySortOrderAscNameAsc(
                        clubId, com.coachrun.entity.enums.CategoryDomain.COURSE).isEmpty()) {
            return false;
        }
        Club club = clubRepository.getReferenceById(clubId);

        // Les séances prescrivent par zone : il faut donc que les zones existent d'abord. C'est le
        // même seed que celui déclenché paresseusement à la première lecture, simplement anticipé.
        zoneSeedService.seedDefaultsIfEmpty(club);
        // Nouveau club : le modèle Vitesses Critiques devient son modèle par défaut.
        zoneSetService.setUpForNewClub(clubId);
        Map<String, UUID> zones = zonesByName(clubId);
        if (zones.isEmpty()) {
            // Sans zone, une séance prescrite ne veut rien dire. On pose tout de même les
            // catégories et les éducatifs : ils ne dépendent de rien.
            log.warn("Jeu de départ : aucune zone pour le club {}, séances non posées", clubId);
            categories(club);
            drills(club);
            strengthSessions(club);
            return true;
        }

        Map<String, SessionCategory> cats = categories(club);
        UUID warmupDrill = drills(club);
        templates(club, cats, zones, warmupDrill);
        moreTemplates(club, cats, zones, warmupDrill);
        strengthSessions(club);
        log.info("Jeu de départ installé pour le club {}", clubId);
        return true;
    }

    // --- Catégories -------------------------------------------------------------

    /**
     * Le rangement de la bibliothèque. Six intitulés qui couvrent le vocabulaire courant d'un
     * plan de course à pied — c'est ce qui permet à la bibliothèque d'être filtrable dès la
     * deuxième séance écrite, au lieu d'être une liste à plat qui grandit.
     */
    private Map<String, SessionCategory> categories(Club club) {
        Map<String, SessionCategory> byName = new HashMap<>();
        int order = 1;
        for (String name : List.of("Footing facile", "Endurance", "Seuil / Tempo",
                "VMA / Fractionné", "Sortie longue", "Compétition / Test")) {
            SessionCategory c = new SessionCategory();
            c.setClub(club);
            c.setName(name);
            c.setSortOrder(order++);
            byName.put(name, categoryRepository.save(c));
        }
        return byName;
    }

    // --- Éducatifs --------------------------------------------------------------

    /**
     * Les gammes de base. Elles s'attachent aux blocs d'échauffement d'une séance ; sans aucun
     * éducatif en bibliothèque, ce lien reste une fonctionnalité que personne ne découvre.
     *
     * @return l'identifiant des montées de genoux, attaché aux échauffements du jeu de départ.
     */
    private UUID drills(Club club) {
        UUID montees = drill(club, "Montées de genoux", RunDrillCategory.TECHNIQUE,
                "Genoux hauts, gainage actif, contact de pied dynamique. 2 × 20 m.").getId();
        drill(club, "Talons-fesses", RunDrillCategory.TECHNIQUE,
                "Ramener le talon sous la fesse, buste droit, fréquence élevée. 2 × 20 m.");
        drill(club, "Pas chassés", RunDrillCategory.TECHNIQUE,
                "Travail latéral, hanches mobiles, épaules face à l'avant. 2 × 20 m par côté.");
        drill(club, "Foulées bondissantes", RunDrillCategory.AMPLITUDE,
                "Grandes foulées, poussée complète, temps de suspension. 3 × 30 m.");
        drill(club, "Griffés", RunDrillCategory.AMPLITUDE,
                "Pied qui griffe le sol vers l'arrière, bassin haut. 3 × 30 m.");
        drill(club, "Lignes droites", RunDrillCategory.AMPLITUDE,
                "4 à 6 × 80 m en accélération progressive, relâché, récupération complète.");
        return montees;
    }

    private RunDrill drill(Club club, String name, RunDrillCategory category, String description) {
        RunDrill d = new RunDrill();
        d.setClub(club);
        d.setName(name);
        d.setCategory(category);
        d.setDescription(description);
        return drillRepository.save(d);
    }

    // --- Séances ----------------------------------------------------------------

    /**
     * Onze premières séances qui couvrent une semaine type de course à pied : deux footings, deux
     * endurances, une sortie longue, deux séances de seuil, deux de VMA (dont les côtes), une
     * spécifique 10 km et un test de calibrage.
     *
     * <p>Elles sont prescrites <b>par zone</b> et non en allure figée : elles se calibrent donc
     * seules sur chaque athlète dès que ses références physiologiques sont saisies. C'est le cœur
     * du produit, et c'est ce qu'une bibliothèque vide empêchait de découvrir.</p>
     */
    private void templates(Club club, Map<String, SessionCategory> cats, Map<String, UUID> z,
                           UUID warmupDrill) {
        UUID footing = zone(z, "Footing facile");
        UUID ef = zone(z, "EF");
        UUID steady = zone(z, "Steady");
        UUID tempo = zone(z, "Tempo");
        UUID seuil2 = zone(z, "Seuil 2 bas");
        UUID p5km = zone(z, "5 km");
        UUID p3km = zone(z, "3 km");
        UUID p10km = zone(z, "10 km");
        UUID vma = zone(z, "1500 m");

        simple(club, cats.get("Footing facile"), "Footing récupération 40 min",
                "Footing très facile", 2400, footing, 2,
                "Vraiment facile : on doit pouvoir tenir une conversation. L'objectif est de "
                        + "récupérer, pas de faire un temps.");
        simple(club, cats.get("Footing facile"), "Footing 30 min", "Footing court", 1800, footing, 2,
                "Séance de déblocage, la veille ou le lendemain d'une séance dure.");
        simple(club, cats.get("Endurance"), "Endurance 1 h", "Endurance fondamentale", 3600, ef, 4,
                "Allure d'endurance, respiration confortable. Rester en fourchette basse si la "
                        + "fatigue de la semaine est élevée.");
        simple(club, cats.get("Endurance"), "Endurance 1 h 15", "Endurance longue", 4500, ef, 4,
                "Le volume de base de la semaine. Boire à partir de 45 min.");

        structured(club, cats.get("Sortie longue"), "Sortie longue 1 h 30", WorkoutType.LONG_RUN,
                "Sortie longue avec fin en Steady", 18000,
                "1 h en endurance puis 20 min en Steady, retour au calme facile. Tester la "
                        + "nutrition de course si une compétition approche.",
                """
                {"warmup":[],
                 "main":[{"id":"m1","type":"long","durationS":3600,"prescription":{"zoneId":"%s"},"rpe":4},
                         {"id":"m2","type":"tempo","durationS":1200,"prescription":{"zoneId":"%s"},"rpe":6,
                          "note":"Fin de sortie en Steady, rester relâché"}],
                 "cooldown":[{"id":"cd1","type":"cooldown","durationS":600,
                              "prescription":{"zoneId":"%s"},"rpe":2}]}"""
                        .formatted(ef, steady, footing));

        structured(club, cats.get("Seuil / Tempo"), "Tempo 20 min", WorkoutType.TEMPO,
                "Tempo continu au seuil", 12000,
                "Trouver l'allure « confortablement difficile » et s'y tenir : ni négociation "
                        + "au début, ni sprint à la fin.",
                """
                {"warmup":[{"id":"wu1","type":"warmup","durationS":900,
                            "prescription":{"zoneId":"%s"},"rpe":3,"drillIds":["%s"]}],
                 "main":[{"id":"m1","type":"tempo","durationS":1200,"prescription":{"zoneId":"%s"},"rpe":6}],
                 "cooldown":[{"id":"cd1","type":"cooldown","durationS":600,
                              "prescription":{"zoneId":"%s"},"rpe":2}]}"""
                        .formatted(ef, warmupDrill, tempo, footing));

        structured(club, cats.get("Seuil / Tempo"), "5 × 1000 m au seuil", WorkoutType.INTERVALS,
                "5 × 1000 m, récupération 90 s", 12000,
                "Séance clé de la semaine. Régularité : les deux derniers 1000 m doivent être "
                        + "les plus rapides. Récupération en trot, sans arrêt complet.",
                """
                {"warmup":[{"id":"wu1","type":"warmup","durationS":1200,
                            "prescription":{"zoneId":"%s"},"rpe":3,"drillIds":["%s"]}],
                 "main":[{"id":"m1","type":"intervals","reps":5,"distanceM":1000,
                          "prescription":{"zoneId":"%s"},"rpe":7,
                          "note":"Même temps à ±2 s d'une répétition à l'autre",
                          "recovery":{"type":"jog","durationS":90,"prescription":{"zoneId":"%s"}}}],
                 "cooldown":[{"id":"cd1","type":"cooldown","durationS":600,
                              "prescription":{"zoneId":"%s"},"rpe":2}]}"""
                        .formatted(ef, warmupDrill, seuil2, footing, footing));

        structured(club, cats.get("VMA / Fractionné"), "10 × 400 m VMA", WorkoutType.INTERVALS,
                "10 × 400 m, récupération 1 min", 10000,
                "Fractionné court. Partir volontairement trop lentement sur les deux premières "
                        + "répétitions : c'est la fin de séance qui compte.",
                """
                {"warmup":[{"id":"wu1","type":"warmup","durationS":1200,
                            "prescription":{"zoneId":"%s"},"rpe":3,"drillIds":["%s"]}],
                 "main":[{"id":"m1","type":"intervals","reps":10,"distanceM":400,
                          "prescription":{"zoneId":"%s"},"rpe":8,
                          "recovery":{"type":"jog","durationS":60,"prescription":{"zoneId":"%s"}}}],
                 "cooldown":[{"id":"cd1","type":"cooldown","durationS":600,
                              "prescription":{"zoneId":"%s"},"rpe":2}]}"""
                        .formatted(ef, warmupDrill, vma, footing, footing));

        structured(club, cats.get("VMA / Fractionné"), "6 × 2 min en côte", WorkoutType.INTERVALS,
                "Côtes, récupération en descente", 9000,
                "Pente de 4 à 6 %. Chercher la poussée et la fréquence, pas la vitesse. "
                        + "Récupération en trottinant la descente.",
                """
                {"warmup":[{"id":"wu1","type":"warmup","durationS":1200,
                            "prescription":{"zoneId":"%s"},"rpe":3,"drillIds":["%s"]}],
                 "main":[{"id":"m1","type":"intervals","reps":6,"durationS":120,
                          "prescription":{"zoneId":"%s"},"rpe":8,
                          "recovery":{"type":"jog","durationS":150,"prescription":{"zoneId":"%s"}}}],
                 "cooldown":[{"id":"cd1","type":"cooldown","durationS":600,
                              "prescription":{"zoneId":"%s"},"rpe":2}]}"""
                        .formatted(ef, warmupDrill, p3km, footing, footing));

        structured(club, cats.get("Compétition / Test"), "3 × 2 km allure 10 km",
                WorkoutType.INTERVALS, "Spécifique 10 km, récupération 2 min", 12000,
                "Caler l'allure de course et la sensation qui va avec. Récupération complète "
                        + "entre les blocs : ce n'est pas une séance de seuil.",
                """
                {"warmup":[{"id":"wu1","type":"warmup","durationS":1200,
                            "prescription":{"zoneId":"%s"},"rpe":3,"drillIds":["%s"]}],
                 "main":[{"id":"m1","type":"intervals","reps":3,"distanceM":2000,
                          "prescription":{"zoneId":"%s"},"rpe":8,
                          "recovery":{"type":"jog","durationS":120,"prescription":{"zoneId":"%s"}}}],
                 "cooldown":[{"id":"cd1","type":"cooldown","durationS":600,
                              "prescription":{"zoneId":"%s"},"rpe":2}]}"""
                        .formatted(ef, warmupDrill, p10km, footing, footing));

        structured(club, cats.get("Compétition / Test"), "Test 5 km", WorkoutType.RACE,
                "Test chronométré sur 5 km", 5000,
                "Test de calibrage : le chrono saisi ensuite dans les records recalcule "
                        + "automatiquement toutes les allures de l'athlète.",
                """
                {"warmup":[{"id":"wu1","type":"warmup","durationS":1200,
                            "prescription":{"zoneId":"%s"},"rpe":3,"drillIds":["%s"]}],
                 "main":[{"id":"m1","type":"race","distanceM":5000,
                          "prescription":{"zoneId":"%s"},"rpe":9,
                          "note":"Effort maximal régulier, pas de départ trop rapide"}],
                 "cooldown":[{"id":"cd1","type":"cooldown","durationS":900,
                              "prescription":{"zoneId":"%s"},"rpe":2}]}"""
                        .formatted(ef, warmupDrill, p5km, footing));
    }

    /** Séance d'un seul bloc : ni échauffement ni retour au calme — le cas du footing. */
    private void simple(Club club, SessionCategory category, String name, String title,
                        int durationS, UUID zoneId, int rpe, String notes) {
        WorkoutTemplate t = base(club, category, name, WorkoutType.ENDURANCE, title,
                durationS / 5 * 1000, notes);
        t.setStructureJson("""
                {"warmup":[],
                 "main":[{"id":"m1","type":"easy","durationS":%d,"prescription":{"zoneId":"%s"},"rpe":%d}],
                 "cooldown":[]}"""
                .formatted(durationS, zoneId, rpe));
        // Une seule étape héritée aussi : la carte ne doit pas annoncer trois étapes là où il n'y
        // a ni échauffement ni retour au calme.
        t.setStepsJson("""
                [{"stepType":"STEADY","repetitions":1,"zone":null,"distanceM":null,"durationS":%d}]"""
                .formatted(durationS));
        templateRepository.save(t);
    }

    private void structured(Club club, SessionCategory category, String name, WorkoutType type,
                            String title, int distanceM, String notes, String structureJson) {
        WorkoutTemplate t = base(club, category, name, type, title, distanceM, notes);
        t.setStructureJson(structureJson);
        t.setStepsJson("[]");
        templateRepository.save(t);
    }

    private WorkoutTemplate base(Club club, SessionCategory category, String name, WorkoutType type,
                                 String title, int distanceM, String notes) {
        WorkoutTemplate t = new WorkoutTemplate();
        t.setClub(club);
        t.setName(name);
        t.setType(type);
        t.setTitle(title);
        t.setTargetDistanceM(distanceM);
        t.setDiscipline(Discipline.ROUTE);
        t.setCategory(category);
        t.setNotes(notes);
        return t;
    }

    // --- Séances, deuxième jeu ----------------------------------------------------

    /**
     * Douze séances de plus, tirées de ce qu'un coach de club écrit vraiment.
     *
     * <p>Le premier jeu couvre une semaine type. Celui-ci vient de la bibliothèque du premier
     * coach pilote, lue après plusieurs semaines d'usage réel : on n'en a gardé que les
     * <b>formes</b> qui reviennent le plus souvent (travail au seuil 1 en blocs longs, seuil 2 en
     * fractionné court, over-under, 30/30, progressif, fartlek, pyramide, spécifiques semi et
     * marathon, test de vitesse critique, déblocage avant course). Aucune séance n'est recopiée :
     * les intitulés, les volumes et les consignes sont réécrits pour un coach qui découvre l'outil.</p>
     */
    private void moreTemplates(Club club, Map<String, SessionCategory> cats, Map<String, UUID> z,
                               UUID warmupDrill) {
        UUID footing = zone(z, "Footing facile");
        UUID ef = zone(z, "EF");
        UUID steady = zone(z, "Steady");
        UUID seuil1 = zone(z, "Seuil 1");
        UUID tempo = zone(z, "Tempo");
        UUID seuil2 = zone(z, "Seuil 2 bas");
        UUID seuil2h = zone(z, "Seuil 2 haut");
        UUID p5km = zone(z, "5 km");
        UUID p3km = zone(z, "3 km");
        UUID vma = zone(z, "1500 m");

        structured(club, cats.get("Footing facile"), "Déblocage 30 min + lignes droites",
                WorkoutType.RECOVERY, "Footing et accélérations, veille de course", 6500,
                "La veille d'une course ou d'une séance clé : on réveille les jambes sans se "
                        + "fatiguer. Les accélérations sont relâchées, jamais à fond.",
                """
                {"warmup":[],
                 "main":[{"id":"m1","type":"easy","durationS":1800,"prescription":{"zoneId":"%s"},"rpe":2},
                         {"id":"m2","type":"intervals","reps":5,"durationS":15,
                          "prescription":{"zoneId":"%s"},"rpe":6,"note":"Accélération progressive, relâché",
                          "recovery":{"type":"walk","durationS":45,"prescription":{"zoneId":"%s"}}}],
                 "cooldown":[]}"""
                        .formatted(footing, vma, footing));

        structured(club, cats.get("Endurance"), "Progressif 60 min", WorkoutType.TEMPO,
                "Footing progressif par paliers de 10 min", 12000,
                "Chaque palier un cran plus vite que le précédent. Le dernier doit rester "
                        + "contrôlé : on finit fort, pas épuisé.",
                """
                {"warmup":[{"id":"wu1","type":"warmup","durationS":600,"prescription":{"zoneId":"%s"},"rpe":2}],
                 "main":[{"id":"m1","type":"easy","durationS":600,"prescription":{"zoneId":"%s"},"rpe":3},
                         {"id":"m2","type":"easy","durationS":600,"prescription":{"zoneId":"%s"},"rpe":4},
                         {"id":"m3","type":"tempo","durationS":600,"prescription":{"zoneId":"%s"},"rpe":5},
                         {"id":"m4","type":"tempo","durationS":600,"prescription":{"zoneId":"%s"},"rpe":6}],
                 "cooldown":[{"id":"cd1","type":"cooldown","durationS":600,"prescription":{"zoneId":"%s"},"rpe":2}]}"""
                        .formatted(footing, ef, steady, seuil1, tempo, footing));

        structured(club, cats.get("Seuil / Tempo"), "3 × 10 min au seuil 1", WorkoutType.THRESHOLD,
                "Seuil 1, récupération 1 min 30", 11000,
                "Séance d'endurance de qualité : longue, mais jamais dure. Si la respiration "
                        + "s'emballe, on est trop vite.",
                """
                {"warmup":[{"id":"wu1","type":"warmup","durationS":1200,
                            "prescription":{"zoneId":"%s"},"rpe":3,"drillIds":["%s"]}],
                 "main":[{"id":"m1","type":"intervals","reps":3,"durationS":600,
                          "prescription":{"zoneId":"%s"},"rpe":5,
                          "recovery":{"type":"jog","durationS":90,"prescription":{"zoneId":"%s"}}}],
                 "cooldown":[{"id":"cd1","type":"cooldown","durationS":600,"prescription":{"zoneId":"%s"},"rpe":2}]}"""
                        .formatted(ef, warmupDrill, seuil1, footing, footing));

        structured(club, cats.get("Seuil / Tempo"), "8 × 3 min au seuil 2", WorkoutType.THRESHOLD,
                "Seuil 2, récupération 1 min", 11000,
                "Fractionné au seuil : on cherche du volume à une intensité tenable, pas de la "
                        + "vitesse. Toutes les répétitions à la même allure.",
                """
                {"warmup":[{"id":"wu1","type":"warmup","durationS":1200,
                            "prescription":{"zoneId":"%s"},"rpe":3,"drillIds":["%s"]}],
                 "main":[{"id":"m1","type":"intervals","reps":8,"durationS":180,
                          "prescription":{"zoneId":"%s"},"rpe":7,
                          "recovery":{"type":"jog","durationS":60,"prescription":{"zoneId":"%s"}}}],
                 "cooldown":[{"id":"cd1","type":"cooldown","durationS":600,"prescription":{"zoneId":"%s"},"rpe":2}]}"""
                        .formatted(ef, warmupDrill, seuil2, footing, footing));

        structured(club, cats.get("Seuil / Tempo"), "20 × (1 min / 1 min)", WorkoutType.INTERVALS,
                "Seuil 2 en fractionné court, récupération 1 min", 10000,
                "Le format court permet d'accumuler 20 min au seuil 2 avec peu de fatigue. "
                        + "La récupération reste trottée.",
                """
                {"warmup":[{"id":"wu1","type":"warmup","durationS":1200,
                            "prescription":{"zoneId":"%s"},"rpe":3,"drillIds":["%s"]}],
                 "main":[{"id":"m1","type":"intervals","reps":20,"durationS":60,
                          "prescription":{"zoneId":"%s"},"rpe":7,
                          "recovery":{"type":"jog","durationS":60,"prescription":{"zoneId":"%s"}}}],
                 "cooldown":[{"id":"cd1","type":"cooldown","durationS":600,"prescription":{"zoneId":"%s"},"rpe":2}]}"""
                        .formatted(ef, warmupDrill, seuil2h, ef, footing));

        structured(club, cats.get("Seuil / Tempo"), "Over-under 6 × (2 min / 2 min)",
                WorkoutType.THRESHOLD, "Alternance au-dessus / en dessous du seuil", 11000,
                "Sans arrêt : 2 min un peu au-dessus du seuil, 2 min un peu en dessous. "
                        + "Apprend à récupérer en restant vite.",
                """
                {"warmup":[{"id":"wu1","type":"warmup","durationS":1200,
                            "prescription":{"zoneId":"%s"},"rpe":3,"drillIds":["%s"]}],
                 "main":[{"id":"m1","type":"intervals","reps":6,
                          "steps":[{"id":"s1","durationS":120,"prescription":{"zoneId":"%s"},"rpe":7},
                                   {"id":"s2","durationS":120,"prescription":{"zoneId":"%s"},"rpe":5}]}],
                 "cooldown":[{"id":"cd1","type":"cooldown","durationS":600,"prescription":{"zoneId":"%s"},"rpe":2}]}"""
                        .formatted(ef, warmupDrill, seuil2h, seuil1, footing));

        structured(club, cats.get("Seuil / Tempo"), "Fartlek 10 × (3 min / 1 min)",
                WorkoutType.TEMPO, "Fartlek, 3 min soutenu, 1 min facile", 12000,
                "Sur route ou en nature, sans montre si possible : 3 min soutenu, 1 min de "
                        + "relâchement en courant.",
                """
                {"warmup":[{"id":"wu1","type":"warmup","durationS":1200,
                            "prescription":{"zoneId":"%s"},"rpe":3,"drillIds":["%s"]}],
                 "main":[{"id":"m1","type":"intervals","reps":10,
                          "steps":[{"id":"s1","durationS":180,"prescription":{"zoneId":"%s"},"rpe":6},
                                   {"id":"s2","durationS":60,"prescription":{"zoneId":"%s"},"rpe":3}]}],
                 "cooldown":[{"id":"cd1","type":"cooldown","durationS":600,"prescription":{"zoneId":"%s"},"rpe":2}]}"""
                        .formatted(ef, warmupDrill, tempo, ef, footing));

        structured(club, cats.get("VMA / Fractionné"), "20 × 30/30", WorkoutType.INTERVALS,
                "30 s vite, 30 s trot", 9000,
                "Le classique pour travailler la VMA. Les 30 s de récupération se courent "
                        + "lentement, sans s'arrêter.",
                """
                {"warmup":[{"id":"wu1","type":"warmup","durationS":1200,
                            "prescription":{"zoneId":"%s"},"rpe":3,"drillIds":["%s"]}],
                 "main":[{"id":"m1","type":"intervals","reps":20,"durationS":30,
                          "prescription":{"zoneId":"%s"},"rpe":8,
                          "recovery":{"type":"jog","durationS":30,"prescription":{"zoneId":"%s"}}}],
                 "cooldown":[{"id":"cd1","type":"cooldown","durationS":600,"prescription":{"zoneId":"%s"},"rpe":2}]}"""
                        .formatted(ef, warmupDrill, p3km, footing, footing));

        structured(club, cats.get("VMA / Fractionné"), "Pyramide 400 → 1000 → 400",
                WorkoutType.INTERVALS, "400-600-800-1000-800-600-400 m, récupération 1 min 30",
                11000,
                "On monte puis on redescend. Les retours en 600 et 400 m doivent être plus "
                        + "rapides qu'à l'aller.",
                """
                {"warmup":[{"id":"wu1","type":"warmup","durationS":1200,
                            "prescription":{"zoneId":"%1$s"},"rpe":3,"drillIds":["%5$s"]}],
                 "main":[{"id":"m1","type":"intervals","reps":1,"distanceM":400,"prescription":{"zoneId":"%2$s"},"rpe":7,
                          "recovery":{"type":"jog","durationS":90,"prescription":{"zoneId":"%4$s"}}},
                         {"id":"m2","type":"intervals","reps":1,"distanceM":600,"prescription":{"zoneId":"%3$s"},"rpe":7,
                          "recovery":{"type":"jog","durationS":90,"prescription":{"zoneId":"%4$s"}}},
                         {"id":"m3","type":"intervals","reps":1,"distanceM":800,"prescription":{"zoneId":"%3$s"},"rpe":8,
                          "recovery":{"type":"jog","durationS":90,"prescription":{"zoneId":"%4$s"}}},
                         {"id":"m4","type":"intervals","reps":1,"distanceM":1000,"prescription":{"zoneId":"%3$s"},"rpe":8,
                          "recovery":{"type":"jog","durationS":90,"prescription":{"zoneId":"%4$s"}}},
                         {"id":"m5","type":"intervals","reps":1,"distanceM":800,"prescription":{"zoneId":"%3$s"},"rpe":8,
                          "recovery":{"type":"jog","durationS":90,"prescription":{"zoneId":"%4$s"}}},
                         {"id":"m6","type":"intervals","reps":1,"distanceM":600,"prescription":{"zoneId":"%2$s"},"rpe":8,
                          "recovery":{"type":"jog","durationS":90,"prescription":{"zoneId":"%4$s"}}},
                         {"id":"m7","type":"intervals","reps":1,"distanceM":400,"prescription":{"zoneId":"%2$s"},"rpe":9}],
                 "cooldown":[{"id":"cd1","type":"cooldown","durationS":600,"prescription":{"zoneId":"%4$s"},"rpe":2}]}"""
                        .formatted(ef, p3km, p5km, footing, warmupDrill));

        structured(club, cats.get("Compétition / Test"), "Spécifique semi 5 × 2 km",
                WorkoutType.INTERVALS, "Allure semi-marathon, récupération 2 min", 16000,
                "Caler l'allure du semi. On doit finir en se disant qu'on aurait pu en faire "
                        + "un de plus.",
                """
                {"warmup":[{"id":"wu1","type":"warmup","durationS":1200,
                            "prescription":{"zoneId":"%s"},"rpe":3,"drillIds":["%s"]}],
                 "main":[{"id":"m1","type":"intervals","reps":5,"distanceM":2000,
                          "prescription":{"zoneId":"%s"},"rpe":6,"note":"Allure semi-marathon visée",
                          "recovery":{"type":"jog","durationS":120,"prescription":{"zoneId":"%s"}}}],
                 "cooldown":[{"id":"cd1","type":"cooldown","durationS":600,"prescription":{"zoneId":"%s"},"rpe":2}]}"""
                        .formatted(ef, warmupDrill, seuil1, footing, footing));

        structured(club, cats.get("Compétition / Test"), "Spécifique marathon 3 × 5 km",
                WorkoutType.LONG_RUN, "Allure marathon, récupération 3 min", 22000,
                "Séance longue à allure marathon : tester aussi le ravitaillement (boisson, gel) "
                        + "comme le jour J.",
                """
                {"warmup":[{"id":"wu1","type":"warmup","durationS":1200,
                            "prescription":{"zoneId":"%s"},"rpe":3}],
                 "main":[{"id":"m1","type":"intervals","reps":3,"distanceM":5000,
                          "prescription":{"zoneId":"%s"},"rpe":6,"note":"Allure marathon visée",
                          "recovery":{"type":"jog","durationS":180,"prescription":{"zoneId":"%s"}}}],
                 "cooldown":[{"id":"cd1","type":"cooldown","durationS":600,"prescription":{"zoneId":"%s"},"rpe":2}]}"""
                        .formatted(ef, steady, footing, footing));

        structured(club, cats.get("Compétition / Test"), "Test vitesse critique 3 min + 12 min",
                WorkoutType.RACE, "Deux efforts maximaux, 20 min de récupération", 9000,
                "Noter la distance parcourue sur chaque effort, puis la saisir dans le test de "
                        + "vitesse critique de l'athlète : ses allures se recalculent.",
                """
                {"warmup":[{"id":"wu1","type":"warmup","durationS":1200,
                            "prescription":{"zoneId":"%s"},"rpe":3,"drillIds":["%s"]}],
                 "main":[{"id":"m1","type":"run","durationS":180,"prescription":{"zoneId":"%s"},"rpe":10,
                          "note":"Effort maximal de 3 min, noter la distance"},
                         {"id":"m2","type":"recovery","durationS":1200,"prescription":{"zoneId":"%s"},"rpe":2},
                         {"id":"m3","type":"run","durationS":720,"prescription":{"zoneId":"%s"},"rpe":9,
                          "note":"Effort maximal régulier de 12 min, noter la distance"}],
                 "cooldown":[{"id":"cd1","type":"cooldown","durationS":600,"prescription":{"zoneId":"%s"},"rpe":2}]}"""
                        .formatted(ef, warmupDrill, vma, footing, p5km, footing));
    }

    // --- Préparation physique -------------------------------------------------------

    /**
     * Trois séances de préparation physique, au poids du corps ou presque.
     *
     * <p>Le catalogue d'exercices était déjà posé (à la première lecture), mais la bibliothèque de
     * séances restait vide : le coach avait les briques, pas de séance prête à planifier. Ces trois
     * formats sont ceux qu'un coach de course prescrit en premier — renforcement général, gainage,
     * prévention pied-cheville — et n'exigent ni barre ni salle.</p>
     *
     * <p>Les exercices sont retrouvés par nom dans le catalogue du club. Un nom absent (catalogue
     * modifié entre-temps) retire l'exercice de la séance plutôt que d'y laisser une référence
     * cassée ; une séance qui se retrouverait vide n'est pas posée.</p>
     */
    private void strengthSessions(Club club) {
        ppCatalogSeedService.seedDefaultsIfEmpty(club);
        Map<String, UUID> ex = new HashMap<>();
        for (PpExercise e : exerciseRepository.findByClubId(club.getId())) {
            ex.putIfAbsent(e.getName(), e.getId());
        }
        Map<String, SessionCategory> cats = new HashMap<>();
        for (SessionCategory c : categoryRepository.findByClubIdAndDomainOrderBySortOrderAscNameAsc(
                club.getId(), CategoryDomain.STRENGTH)) {
            cats.putIfAbsent(c.getName(), c);
        }

        strength(club, cats.get("Circuit général"), "Renforcement coureur 30 min",
                "Séance de base, sans matériel ou presque, à placer sur une journée facile. "
                        + "Charge légère : la technique d'abord.",
                List.of(
                        block("b0", "ECHAUFFEMENT", "CLASSIQUE", List.of(
                                item(ex, "Mobilité de cheville au mur", 2, "\"volumeType\":\"REPS\",\"repsFixed\":10,\"sideMode\":\"PAR_COTE\""),
                                item(ex, "Ouverture de hanche 90/90", 2, "\"volumeType\":\"REPS\",\"repsFixed\":8,\"sideMode\":\"PAR_COTE\""))),
                        block("b1", "PRINCIPAL", "CLASSIQUE", List.of(
                                item(ex, "Fente avant", 3, "\"volumeType\":\"REPS\",\"repsFixed\":10,\"sideMode\":\"PAR_COTE\",\"effortRefType\":\"RPE\",\"rpeMin\":6,\"restSecMin\":60,\"restSecMax\":90"),
                                item(ex, "Pont fessier unilatéral", 3, "\"volumeType\":\"REPS\",\"repsFixed\":12,\"sideMode\":\"PAR_COTE\",\"effortRefType\":\"RPE\",\"rpeMin\":6,\"restSecMin\":45,\"restSecMax\":60"),
                                item(ex, "Extension mollets debout", 3, "\"volumeType\":\"REPS\",\"repsFixed\":15,\"effortRefType\":\"RPE\",\"rpeMin\":6,\"restSecMin\":45,\"restSecMax\":60"),
                                item(ex, "Pompes", 3, "\"volumeType\":\"REPS\",\"repsFixed\":10,\"effortRefType\":\"RPE\",\"rpeMin\":6,\"restSecMin\":60,\"restSecMax\":90"))),
                        block("b2", "ACCESSOIRE", "ISOMETRIE", List.of(
                                item(ex, "Gainage ventral", 3, "\"volumeType\":\"DUREE\",\"durationSec\":30,\"restSecMin\":30,\"restSecMax\":45"),
                                item(ex, "Gainage latéral", 2, "\"volumeType\":\"DUREE\",\"durationSec\":30,\"sideMode\":\"PAR_COTE\",\"restSecMin\":30,\"restSecMax\":45")))));

        strength(club, cats.get("Gainage & tronc"), "Gainage 15 min",
                "Circuit court, à glisser après un footing. Qualité de position avant la durée : "
                        + "on s'arrête quand le dos se creuse.",
                List.of(block("b0", "PRINCIPAL", "CIRCUIT", List.of(
                        item(ex, "Gainage ventral", 3, "\"volumeType\":\"DUREE\",\"durationSec\":40,\"restSecMin\":20,\"restSecMax\":20"),
                        item(ex, "Gainage latéral", 3, "\"volumeType\":\"DUREE\",\"durationSec\":30,\"sideMode\":\"PAR_COTE\",\"restSecMin\":20,\"restSecMax\":20"),
                        item(ex, "Dead bug", 3, "\"volumeType\":\"REPS\",\"repsFixed\":10,\"sideMode\":\"ALTERNE\",\"restSecMin\":20,\"restSecMax\":20"),
                        item(ex, "Bird dog", 3, "\"volumeType\":\"REPS\",\"repsFixed\":10,\"sideMode\":\"ALTERNE\",\"restSecMin\":20,\"restSecMax\":20")))));

        strength(club, cats.get("Prévention & réathlétisation"), "Prévention pied-cheville 15 min",
                "Mollets, tendon d'Achille et pied : la zone la plus sollicitée du coureur. "
                        + "Deux à trois fois par semaine, en fin de footing.",
                List.of(block("b0", "PRINCIPAL", "CLASSIQUE", List.of(
                        item(ex, "Excentrique mollet sur marche", 3, "\"volumeType\":\"REPS\",\"repsFixed\":12,\"sideMode\":\"PAR_COTE\",\"tempo\":\"3-0-1-0\",\"restSecMin\":45,\"restSecMax\":60"),
                        item(ex, "Extension mollets genou fléchi", 3, "\"volumeType\":\"REPS\",\"repsFixed\":15,\"restSecMin\":45,\"restSecMax\":60"),
                        item(ex, "Équilibre unipodal", 3, "\"volumeType\":\"DUREE\",\"durationSec\":30,\"sideMode\":\"PAR_COTE\",\"restSecMin\":15,\"restSecMax\":30"),
                        item(ex, "Toe yoga", 2, "\"volumeType\":\"REPS\",\"repsFixed\":10,\"restSecMin\":15,\"restSecMax\":30")))));
    }

    /** Une séance de force, posée seulement si au moins un exercice a été retrouvé. */
    private void strength(Club club, SessionCategory category, String name, String notes,
                          List<String> blocks) {
        List<String> kept = blocks.stream().filter(b -> !b.contains("\"exercises\":[]")).toList();
        if (kept.isEmpty()) {
            log.warn("Jeu de départ : séance de force « {} » non posée, exercices introuvables", name);
            return;
        }
        StrengthSession s = new StrengthSession();
        s.setClub(club);
        s.setName(name);
        s.setNotes(notes);
        s.setCategory(category);
        s.setStructureJson("{\"blocks\":[" + String.join(",", kept) + "]}");
        strengthSessionRepository.save(s);
    }

    private static String block(String id, String type, String format, List<String> items) {
        List<String> found = items.stream().filter(i -> i != null).toList();
        return "{\"id\":\"%s\",\"blockType\":\"%s\",\"format\":\"%s\",\"exercises\":[%s]}"
                .formatted(id, type, format, String.join(",", found));
    }

    /** Un exercice prescrit, ou {@code null} si le catalogue du club ne le contient pas. */
    private static String item(Map<String, UUID> exercises, String name, int sets, String prescription) {
        UUID id = exercises.get(name);
        if (id == null) {
            return null;
        }
        return ("{\"exerciseId\":\"%s\",\"exerciseName\":\"%s\",\"setType\":\"STANDARD\","
                + "\"prescription\":{\"sets\":%d,%s}}").formatted(id, name, sets, prescription);
    }

    // --- Zones ------------------------------------------------------------------

    /**
     * Zones par nom, celles du modèle par défaut d'abord : les séances du kit sont écrites sur
     * l'échelle que verront les athlètes. « Footing facile » s'appelle « Récupération » dans le
     * modèle Vitesses Critiques.
     */
    private Map<String, UUID> zonesByName(UUID clubId) {
        Map<String, UUID> byName = new HashMap<>();
        for (TrainingZone z : zoneSetService.zones(clubId, null)) {
            byName.putIfAbsent(z.getName(), z.getId());
        }
        if (!byName.containsKey("Footing facile") && byName.containsKey("Récupération")) {
            byName.put("Footing facile", byName.get("Récupération"));
        }
        for (TrainingZone z : zoneRepository.findByClubIdOrderBySortOrderAscNameAsc(clubId)) {
            byName.putIfAbsent(z.getName(), z.getId());
        }
        return byName;
    }

    /**
     * Zone par nom, avec repli sur la première du club.
     *
     * <p>Le repli n'est pas un détail : un club dont les zones auraient été renommées avant
     * l'installation produirait sinon des séances prescrites sur une zone inexistante, donc
     * illisibles. Mieux vaut une prescription approximative qu'une séance cassée.</p>
     */
    private UUID zone(Map<String, UUID> zones, String name) {
        UUID id = zones.get(name);
        return id != null ? id : zones.values().iterator().next();
    }
}
