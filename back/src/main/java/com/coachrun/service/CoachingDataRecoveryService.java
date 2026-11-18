package com.coachrun.service;

import com.coachrun.entity.Athlete;
import com.coachrun.entity.AthleteAccount;
import com.coachrun.entity.User;
import com.coachrun.entity.enums.AdminAuditAction;
import com.coachrun.entity.enums.AdminAuditTarget;
import com.coachrun.exception.ConflictException;
import com.coachrun.exception.NotFoundException;
import com.coachrun.repository.AthleteAccountRepository;
import com.coachrun.repository.AthleteRepository;
import com.coachrun.repository.DeviceConnectionRepository;
import com.coachrun.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Rattrapage, pour les athlètes qui ont changé de coach avant que leur profil ne les suive.
 *
 * <p>Leurs records, seuils, montres et calendrier passé sont restés sur la fiche de l'ancien
 * coach. Ce rattrapage les ramène, par copie, dans leur espace personnel puis sur leur fiche
 * actuelle — sans rien écraser de ce que le nouveau coach a saisi depuis. Le calendrier passé
 * n'est transmis au coach actuel que si l'athlète l'a demandé.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CoachingDataRecoveryService {

    private final UserRepository userRepository;
    private final AthleteAccountRepository accountRepository;
    private final AthleteRepository athleteRepository;
    private final DeviceConnectionRepository connectionRepository;
    private final PersonalSpaceService personalSpace;
    private final AthleteCarryOverService carryOver;
    private final AdminAuditService audit;

    public record Result(int formerFiches, int devicesMoved, boolean sharedWithCurrentCoach) {
    }

    @Transactional
    public Result recover(UUID userId, boolean shareWithCurrentCoach) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("Compte introuvable."));
        AthleteAccount account = accountRepository.findByUserId(userId)
                .orElseThrow(() -> new ConflictException("Ce compte n'a pas de compte athlète."));
        Athlete personal = personalSpace.ensure(account);
        Athlete current = user.getAthlete() != null ? user.getAthlete() : personal;

        List<Athlete> former = athleteRepository.findByAccountId(account.getId()).stream()
                .filter(a -> !a.getId().equals(current.getId()) && !a.getId().equals(personal.getId()))
                .filter(a -> !PersonalSpaceService.isPersonal(a))
                // Du plus récent au plus ancien : en mode « remplir les vides », c'est la fiche la
                // plus récente qui fixe chaque valeur.
                .sorted(Comparator.comparing(Athlete::getCreatedAt).reversed())
                .toList();

        int devices = 0;
        for (Athlete old : former) {
            carryOver.fillProfile(old, personal);
            personalSpace.copyHistory(old, personal);
            int before = connectionRepository.findByAthleteId(current.getId()).size();
            personalSpace.moveDevices(old, current);
            devices += connectionRepository.findByAthleteId(current.getId()).size() - before;
        }
        boolean shared = false;
        if (!current.getId().equals(personal.getId())) {
            carryOver.fillProfile(personal, current);
            if (shareWithCurrentCoach) {
                personalSpace.copyHistory(personal, current);
                shared = true;
            }
        }
        Result result = new Result(former.size(), devices, shared);
        audit.record(AdminAuditAction.USER_UPDATED, AdminAuditTarget.USER, user.getId(), user.getEmail(),
                "Rattrapage du changement de coach : " + result.formerFiches() + " ancienne(s) fiche(s), "
                        + result.devicesMoved() + " montre(s) rebranchée(s), calendrier passé "
                        + (shared ? "transmis" : "non transmis") + " au coach actuel.");
        log.info("Rattrapage changement de coach pour {} : {}", user.getId(), result);
        return result;
    }
}
