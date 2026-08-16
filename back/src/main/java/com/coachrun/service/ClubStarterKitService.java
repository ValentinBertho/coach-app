package com.coachrun.service;

import com.coachrun.entity.Club;
import com.coachrun.entity.RunDrill;
import com.coachrun.entity.SessionCategory;
import com.coachrun.entity.TrainingZone;
import com.coachrun.entity.WorkoutTemplate;
import com.coachrun.entity.enums.Discipline;
import com.coachrun.entity.enums.RunDrillCategory;
import com.coachrun.entity.enums.WorkoutType;
import com.coachrun.repository.RunDrillRepository;
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
    private final com.coachrun.repository.ClubRepository clubRepository;

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
        Map<String, UUID> zones = zonesByName(clubId);
        if (zones.isEmpty()) {
            // Sans zone, une séance prescrite ne veut rien dire. On pose tout de même les
            // catégories et les éducatifs : ils ne dépendent de rien.
            log.warn("Jeu de départ : aucune zone pour le club {}, séances non posées", clubId);
            categories(club);
            drills(club);
            return true;
        }

        Map<String, SessionCategory> cats = categories(club);
        UUID warmupDrill = drills(club);
        templates(club, cats, zones, warmupDrill);
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
     * Onze séances qui couvrent une semaine type de course à pied : deux footings, deux
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

    // --- Zones ------------------------------------------------------------------

    private Map<String, UUID> zonesByName(UUID clubId) {
        Map<String, UUID> byName = new HashMap<>();
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
