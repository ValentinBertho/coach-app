package com.coachrun.service;

import com.coachrun.entity.Athlete;
import com.coachrun.entity.AthletePerformance;
import com.coachrun.entity.DailyCheckIn;
import com.coachrun.entity.LactateTest;
import com.coachrun.entity.LactateTestStep;
import com.coachrun.entity.RaceObjective;
import com.coachrun.entity.Workout;
import com.coachrun.entity.WorkoutStep;
import com.coachrun.entity.enums.WorkoutStatus;
import com.coachrun.repository.AthletePerformanceRepository;
import com.coachrun.repository.AthleteRepository;
import com.coachrun.repository.DailyCheckInRepository;
import com.coachrun.repository.LactateTestRepository;
import com.coachrun.repository.RaceObjectiveRepository;
import com.coachrun.repository.WorkoutRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Ce qui appartient à l'athlète le suit d'une fiche à l'autre.
 *
 * <h2>Le problème</h2>
 *
 * <p>Chaque espace (club, coach, espace personnel) a sa propre fiche pour la même personne. Au
 * changement de coach, la nouvelle fiche naissait vierge : plus de records, plus de seuils, plus
 * de VMA ni de FC — donc plus aucune zone calculée — et un calendrier passé vide. Tout restait sur
 * la fiche de l'ancien coach, que l'athlète ne voit plus.</p>
 *
 * <h2>Deux paquets</h2>
 *
 * <ul>
 *   <li><b>Le profil</b> ({@link #carryProfile}) : seuils, FC, VMA, poids, records, tests lactate,
 *   objectifs de course. C'est ce qui sert à calculer les zones ; il suit l'athlète partout.</li>
 *   <li><b>Le calendrier passé</b> ({@link #copyPastWorkouts}) : les séances déjà passées, avec
 *   ses retours. L'athlète le garde toujours dans son espace ; il n'est transmis au nouveau coach
 *   que si l'athlète l'a choisi (avec ses sorties).</li>
 * </ul>
 *
 * <p>Tout est <b>copié</b>, jamais déplacé : l'ancien coach garde son suivi tel qu'il l'a laissé.
 * Chaque copie est idempotente (rien n'est dupliqué si on la rejoue, ou si l'athlète revient chez
 * un coach dont la fiche a déjà ces données).</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AthleteCarryOverService {

    private final AthleteRepository athleteRepository;
    private final AthletePerformanceRepository performanceRepository;
    private final LactateTestRepository lactateTestRepository;
    private final RaceObjectiveRepository objectiveRepository;
    private final DailyCheckInRepository checkInRepository;
    private final WorkoutRepository workoutRepository;
    private final AthletePhysioService physioService;
    private final ClockService clock;

    /**
     * Recopie le profil physiologique de {@code from} vers {@code to}, puis recalcule le VDOT et
     * les zones de {@code to} dans son propre modèle de zones.
     *
     * <p>La fiche source est toujours la plus récente (celle qu'on quitte) : ses valeurs
     * renseignées l'emportent ; une valeur vide ne vient jamais effacer une valeur connue.</p>
     */
    @Transactional
    public void carryProfile(Athlete from, Athlete to) {
        carryProfile(from, to, true);
    }

    /**
     * Variante de rattrapage : ne remplit que les valeurs vides de {@code to}, pour ne rien
     * écraser de ce que le nouveau coach a pu saisir depuis le changement.
     */
    @Transactional
    public void fillProfile(Athlete from, Athlete to) {
        carryProfile(from, to, false);
    }

    private void carryProfile(Athlete from, Athlete to, boolean overwrite) {
        if (same(from, to)) {
            return;
        }
        if (take(from.getHrMax(), to.getHrMax(), overwrite)) to.setHrMax(from.getHrMax());
        if (take(from.getHrRest(), to.getHrRest(), overwrite)) to.setHrRest(from.getHrRest());
        if (take(from.getVma(), to.getVma(), overwrite)) to.setVma(from.getVma());
        if (take(from.getWeightKg(), to.getWeightKg(), overwrite)) to.setWeightKg(from.getWeightKg());
        if (take(from.getLt1Ms(), to.getLt1Ms(), overwrite)) to.setLt1Ms(from.getLt1Ms());
        if (take(from.getLt2Ms(), to.getLt2Ms(), overwrite)) to.setLt2Ms(from.getLt2Ms());
        if (take(from.getVcMs(), to.getVcMs(), overwrite)) to.setVcMs(from.getVcMs());
        if (take(from.getFcLt1(), to.getFcLt1(), overwrite)) to.setFcLt1(from.getFcLt1());
        if (take(from.getFcLt2(), to.getFcLt2(), overwrite)) to.setFcLt2(from.getFcLt2());
        if (overwrite && from.getDiscipline() != null) to.setDiscipline(from.getDiscipline());
        if (from.getDeviceConsentAt() != null && to.getDeviceConsentAt() == null) {
            to.setDeviceConsentAt(from.getDeviceConsentAt());
        }
        athleteRepository.save(to);

        int perfs = copyPerformances(from, to);
        int tests = copyLactateTests(from, to);
        int objectives = copyObjectives(from, to);
        // VDOT, allures d'équivalence et zones : recalculés sur la fiche d'arrivée, dans le modèle
        // de zones de son espace — les zones d'un club ne se recopient pas telles quelles.
        try {
            physioService.recomputeAfterCarryOver(to);
        } catch (RuntimeException e) {
            // Un recalcul raté ne doit pas faire échouer un changement de coach : le coach (ou le
            // prochain record saisi) relancera le calcul.
            log.warn("Recalcul VDOT/zones impossible après report sur la fiche {} : {}", to.getId(), e.getMessage());
        }
        log.info("Profil reporté de la fiche {} vers {} ({} records, {} tests, {} objectifs)",
                from.getId(), to.getId(), perfs, tests, objectives);
    }

    /**
     * Recopie les séances passées (et celles déjà faites) avec les retours de l'athlète, et ses
     * ressentis quotidiens. Les séances à venir ne sont pas concernées : elles sont retirées à la
     * fin du coaching.
     *
     * @return correspondance ancienne séance → copie, pour rapprocher les sorties recopiées
     */
    @Transactional
    public Map<UUID, UUID> copyPastWorkouts(Athlete from, Athlete to) {
        Map<UUID, UUID> mapping = new HashMap<>();
        if (same(from, to)) {
            return mapping;
        }
        LocalDate today = clock.today();
        Set<String> existing = new HashSet<>();
        for (Workout w : workoutRepository.findByAthleteIdOrderByScheduledDateAsc(to.getId())) {
            existing.add(workoutKey(w));
        }
        for (Workout src : workoutRepository.findByAthleteIdOrderByScheduledDateAsc(from.getId())) {
            boolean past = src.getScheduledDate() != null && src.getScheduledDate().isBefore(today);
            if (!past && src.getStatus() == WorkoutStatus.PLANNED) {
                continue;
            }
            if (!existing.add(workoutKey(src))) {
                continue;
            }
            Workout copy = new Workout();
            BeanUtils.copyProperties(src, copy,
                    "id", "createdAt", "updatedAt", "version", "club", "athlete", "steps",
                    // Le plan et le modèle de séance vivent dans l'espace de l'ancien coach.
                    "planId", "sourceTemplateId");
            copy.setClub(to.getClub());
            copy.setAthlete(to);
            for (WorkoutStep s : src.getSteps()) {
                WorkoutStep step = new WorkoutStep();
                BeanUtils.copyProperties(s, step, "id", "createdAt", "updatedAt", "version", "workout");
                step.setWorkout(copy);
                copy.getSteps().add(step);
            }
            copy = workoutRepository.save(copy);
            mapping.put(src.getId(), copy.getId());
        }
        int checkIns = copyCheckIns(from, to);
        log.info("Calendrier passé recopié de la fiche {} vers {} ({} séances, {} ressentis)",
                from.getId(), to.getId(), mapping.size(), checkIns);
        return mapping;
    }

    private int copyPerformances(Athlete from, Athlete to) {
        Set<String> existing = new HashSet<>();
        for (AthletePerformance p : performanceRepository.findByAthleteIdOrderByDateSetDescCreatedAtDesc(to.getId())) {
            existing.add(p.getDistance() + "|" + p.getTimeSeconds() + "|" + p.getDateSet());
        }
        int copied = 0;
        for (AthletePerformance p : performanceRepository.findByAthleteIdOrderByDateSetDescCreatedAtDesc(from.getId())) {
            if (!existing.add(p.getDistance() + "|" + p.getTimeSeconds() + "|" + p.getDateSet())) {
                continue;
            }
            AthletePerformance copy = new AthletePerformance();
            copy.setAthlete(to);
            copy.setDistance(p.getDistance());
            copy.setTimeSeconds(p.getTimeSeconds());
            copy.setDateSet(p.getDateSet());
            performanceRepository.save(copy);
            copied++;
        }
        return copied;
    }

    private int copyLactateTests(Athlete from, Athlete to) {
        Set<String> existing = new HashSet<>();
        for (LactateTest t : lactateTestRepository.findByAthleteIdOrderByTestDateDesc(to.getId())) {
            existing.add(t.getTestType() + "|" + t.getTestDate());
        }
        int copied = 0;
        for (LactateTest t : lactateTestRepository.findByAthleteIdOrderByTestDateDesc(from.getId())) {
            if (!existing.add(t.getTestType() + "|" + t.getTestDate())) {
                continue;
            }
            LactateTest copy = new LactateTest();
            BeanUtils.copyProperties(t, copy, "id", "createdAt", "updatedAt", "version", "club", "athlete", "steps");
            copy.setClub(to.getClub());
            copy.setAthlete(to);
            for (LactateTestStep s : t.getSteps()) {
                LactateTestStep step = new LactateTestStep();
                BeanUtils.copyProperties(s, step, "id", "createdAt", "updatedAt", "version", "test");
                step.setTest(copy);
                copy.getSteps().add(step);
            }
            lactateTestRepository.save(copy);
            copied++;
        }
        return copied;
    }

    private int copyObjectives(Athlete from, Athlete to) {
        Set<String> existing = new HashSet<>();
        for (RaceObjective o : objectiveRepository.findByAthleteIdOrderByRaceDateAsc(to.getId())) {
            existing.add(objectiveKey(o));
        }
        int copied = 0;
        for (RaceObjective o : objectiveRepository.findByAthleteIdOrderByRaceDateAsc(from.getId())) {
            if (!existing.add(objectiveKey(o))) {
                continue;
            }
            RaceObjective copy = new RaceObjective();
            BeanUtils.copyProperties(o, copy, "id", "createdAt", "updatedAt", "version", "club", "athlete");
            copy.setClub(to.getClub());
            copy.setAthlete(to);
            objectiveRepository.save(copy);
            copied++;
        }
        return copied;
    }

    private int copyCheckIns(Athlete from, Athlete to) {
        Set<LocalDate> existing = new HashSet<>();
        for (DailyCheckIn c : checkInRepository.findByAthleteIdOrderByCheckDateDesc(to.getId())) {
            existing.add(c.getCheckDate());
        }
        int copied = 0;
        for (DailyCheckIn c : checkInRepository.findByAthleteIdOrderByCheckDateDesc(from.getId())) {
            if (!existing.add(c.getCheckDate())) {
                continue;
            }
            DailyCheckIn copy = new DailyCheckIn();
            BeanUtils.copyProperties(c, copy, "id", "createdAt", "updatedAt", "version", "club", "athlete");
            copy.setAthlete(to);
            checkInRepository.save(copy);
            copied++;
        }
        return copied;
    }

    private static boolean take(Object source, Object target, boolean overwrite) {
        return source != null && (overwrite || target == null);
    }

    private static String workoutKey(Workout w) {
        return w.getScheduledDate() + "|" + w.getType() + "|" + Objects.toString(w.getTitle(), "")
                + "|" + w.getOrderIndex();
    }

    private static String objectiveKey(RaceObjective o) {
        return o.getRaceDate() + "|" + Objects.toString(o.getName(), "").trim().toLowerCase();
    }

    private static boolean same(Athlete from, Athlete to) {
        return from == null || to == null || from.getId().equals(to.getId());
    }
}
