package com.coachrun.config;

import com.coachrun.entity.Athlete;
import com.coachrun.entity.CoachAthleteRelation;
import com.coachrun.entity.User;
import com.coachrun.entity.enums.UserRole;
import com.coachrun.repository.AthleteRepository;
import com.coachrun.repository.CoachAthleteRelationRepository;
import com.coachrun.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.UUID;

/**
 * Backfill idempotent du modèle multi-coach : garantit que chaque athlète possède une relation
 * référent active (rattachée à son club, référent = head coach du club). Sans elle, le durcissement
 * d'accès ({@code AthleteAccessValidator}) retomberait sur le fallback club-level pour les données
 * antérieures. S'exécute une fois au démarrage et ne touche que les athlètes sans référent
 * (les athlètes créés depuis le câblage ou par le démo en ont déjà un).
 */
@Slf4j
@Component
@Order(50)
@RequiredArgsConstructor
public class MultiCoachBackfill implements ApplicationRunner {

    private final AthleteRepository athleteRepository;
    private final CoachAthleteRelationRepository relationRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        Set<UUID> withReferent = relationRepository.findAthleteIdsWithActiveReferent();
        int created = 0;
        for (Athlete athlete : athleteRepository.findAll()) {
            if (withReferent.contains(athlete.getId()) || athlete.getClub() == null) {
                continue;
            }
            User headCoach = userRepository
                    .findFirstByClubIdAndRole(athlete.getClub().getId(), UserRole.HEAD_COACH)
                    .orElse(null);
            if (headCoach == null) {
                continue; // pas de référent identifiable → laissé au fallback club-level
            }
            CoachAthleteRelation rel = new CoachAthleteRelation();
            rel.setAthlete(athlete);
            rel.setCoach(headCoach);
            rel.setClub(athlete.getClub());
            rel.setReferent(true);
            rel.setActive(true);
            relationRepository.save(rel);
            created++;
        }
        if (created > 0) {
            log.info("Backfill multi-coach : {} relation(s) référent créée(s).", created);
        }
    }
}
