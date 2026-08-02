package com.coachrun;

import com.coachrun.dto.response.AthleteExportResponse;
import com.coachrun.entity.AthleteUnavailability;
import com.coachrun.entity.DailyCheckIn;
import com.coachrun.entity.RaceObjective;
import com.coachrun.entity.enums.RacePriority;
import com.coachrun.entity.enums.UnavailabilityReason;
import com.coachrun.repository.AthleteRepository;
import com.coachrun.repository.AthleteUnavailabilityRepository;
import com.coachrun.repository.DailyCheckInRepository;
import com.coachrun.repository.RaceObjectiveRepository;
import com.coachrun.service.DemoSeedService;
import com.coachrun.service.GdprService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Portabilité RGPD (art. 20). L'export ne couvrait que le profil, les séances et les activités :
 * trois familles sur les vingt-deux rattachées à un athlète. Tout ce que l'athlète a déclaré ou
 * produit — check-ins, tests, records, objectifs, indisponibilités, messages, force, zones — doit
 * s'y retrouver, sinon le dossier de portabilité est incomplet.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class GdprExportTest {

    @Autowired private DemoSeedService demoSeedService;
    @Autowired private GdprService gdprService;
    @Autowired private AthleteRepository athleteRepository;
    @Autowired private DailyCheckInRepository checkInRepository;
    @Autowired private RaceObjectiveRepository raceRepository;
    @Autowired private AthleteUnavailabilityRepository unavailabilityRepository;

    private UUID athleteId;

    @BeforeEach
    void setUp() {
        demoSeedService.seed();
        // Athlète du seed le mieux fourni : celui qui porte des séances ET des activités.
        athleteId = athleteRepository.findAll().stream()
                .filter(a -> a.getHealthDataConsentAt() != null)
                .findFirst()
                .orElseGet(() -> athleteRepository.findAll().get(0))
                .getId();
    }

    @Test
    void exportCoversEveryFamilyOfAthleteData() {
        // Le seed ne garantit pas ces trois familles : on les pose explicitement pour vérifier
        // qu'elles ressortent bien dans l'export.
        var athlete = athleteRepository.findById(athleteId).orElseThrow();

        DailyCheckIn checkIn = new DailyCheckIn();
        checkIn.setAthlete(athlete);
        checkIn.setCheckDate(LocalDate.now());
        checkIn.setFatigue(3);
        checkIn.setPain(1);
        checkInRepository.save(checkIn);

        RaceObjective race = new RaceObjective();
        race.setClub(athlete.getClub());
        race.setAthlete(athlete);
        race.setName("Marathon export");
        race.setRaceDate(LocalDate.now().plusMonths(3));
        race.setPriority(RacePriority.A);
        raceRepository.save(race);

        AthleteUnavailability off = new AthleteUnavailability();
        off.setClub(athlete.getClub());
        off.setAthlete(athlete);
        off.setReason(UnavailabilityReason.INJURY);
        off.setStartDate(LocalDate.now().minusDays(3));
        off.setEndDate(LocalDate.now().plusDays(3));
        unavailabilityRepository.save(off);

        AthleteExportResponse export = gdprService.export(athleteId);

        // Profil et traçabilité du consentement.
        assertThat(export.profile()).isNotNull();
        assertThat(export.exportedAt()).isNotNull();

        // Familles historiques.
        assertThat(export.workouts()).isNotNull();
        assertThat(export.activities()).isNotNull();

        // Familles ajoutées : au moins une ligne de chacune de celles qu'on vient de poser.
        assertThat(export.dailyCheckIns()).isNotEmpty();
        assertThat(export.raceObjectives()).extracting("name").contains("Marathon export");
        assertThat(export.unavailabilities()).isNotEmpty();

        // Les autres familles sont présentes dans le contrat, même vides pour cet athlète :
        // un export ne doit jamais renvoyer null (le client ne saurait pas distinguer
        // « aucune donnée » de « famille oubliée »).
        assertThat(export.lactateTests()).isNotNull();
        assertThat(export.performances()).isNotNull();
        assertThat(export.messages()).isNotNull();
        assertThat(export.strengthTests()).isNotNull();
        assertThat(export.strengthResults()).isNotNull();
        assertThat(export.zoneValues()).isNotNull();
    }

    /** Un athlète du seed complet ressort avec ses données de santé, pas seulement son profil. */
    @Test
    void seededAthleteExportCarriesHealthData() {
        AthleteExportResponse export = gdprService.export(athleteId);

        assertThat(export.workouts()).isNotEmpty();
        // Les valeurs de zones sont générées au seed (resync) : elles doivent suivre l'athlète.
        assertThat(export.zoneValues()).isNotEmpty();
    }
}
