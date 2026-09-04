package com.coachrun.config;

import com.coachrun.entity.Athlete;
import com.coachrun.entity.Club;
import com.coachrun.entity.ClubMember;
import com.coachrun.entity.CoachAthleteRelation;
import com.coachrun.entity.User;
import com.coachrun.entity.enums.ClubRole;
import com.coachrun.entity.enums.UserRole;
import com.coachrun.repository.AthleteRepository;
import com.coachrun.repository.ClubMemberRepository;
import com.coachrun.repository.ClubRepository;
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
 * référent (rattachée à son club, référent = head coach du club). Sans elle, le durcissement
 * d'accès ({@code AthleteAccessValidator}) retomberait sur le fallback club-level pour les données
 * antérieures. S'exécute à chaque démarrage et ne touche que les athlètes qui n'ont
 * <b>jamais</b> eu de référent (ceux créés depuis le câblage ou par le démo en ont déjà un).
 *
 * <p><b>« Jamais eu », et non « n'en a pas d'actif ».</b> La nuance décide de la correction d'une
 * révocation, et elle décide aussi du démarrage. Ce backfill s'exécutant à chaque démarrage,
 * prendre pour clé l'existence d'un référent <em>actif</em> revenait à retraiter tout athlète dont
 * la relation venait d'être close. Quand le coach détaché était le head coach du club — le cas
 * nominal d'un indépendant — la réinsertion de la même paire (coach, athlète) violait
 * {@code uq_coach_athlete}, et l'exception levée dans un {@link ApplicationRunner} empêchait
 * l'application de démarrer. Quand il ne l'était pas, la relation était recréée au profit du head
 * coach, à qui l'on rendait en silence un accès que personne ne lui avait donné.</p>
 */
@Slf4j
@Component
@Order(50)
@RequiredArgsConstructor
public class MultiCoachBackfill implements ApplicationRunner {

    private final AthleteRepository athleteRepository;
    private final CoachAthleteRelationRepository relationRepository;
    private final UserRepository userRepository;
    private final ClubRepository clubRepository;
    private final ClubMemberRepository clubMemberRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        ensureClubOwners();

        Set<UUID> withReferent = relationRepository.findAthleteIdsWithAnyReferent();
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

    /** Garantit que le head coach de chaque club en est membre OWNER (membership multi-coach). */
    private void ensureClubOwners() {
        int created = 0;
        for (Club club : clubRepository.findAll()) {
            User headCoach = userRepository
                    .findFirstByClubIdAndRole(club.getId(), UserRole.HEAD_COACH).orElse(null);
            if (headCoach == null
                    || clubMemberRepository.findByClubIdAndCoachIdAndActiveTrue(club.getId(), headCoach.getId()).isPresent()) {
                continue;
            }
            ClubMember owner = new ClubMember();
            owner.setClub(club);
            owner.setCoach(headCoach);
            owner.setClubRole(ClubRole.OWNER);
            owner.setActive(true);
            clubMemberRepository.save(owner);
            created++;
        }
        if (created > 0) {
            log.info("Backfill multi-coach : {} propriétaire(s) de club créé(s).", created);
        }
    }
}
