package com.coachrun;

import com.coachrun.entity.Athlete;
import com.coachrun.entity.User;
import com.coachrun.repository.AthleteAccountRepository;
import com.coachrun.repository.AthleteRepository;
import com.coachrun.repository.UserRepository;
import com.coachrun.service.AthleteAccountService;
import com.coachrun.service.DemoSeedService;
import com.coachrun.service.GdprService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * « Supprimer mon compte » et le compte athlète.
 *
 * <p>Un athlète invité par son coach n'avait pas de compte athlète : son profil répondait
 * « Ressource introuvable » et il ne pouvait demander aucun coach. Et dès qu'il en avait un, la
 * suppression de son compte échouait sur la clé étrangère {@code athlete_accounts.user_id}.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AthleteAccountErasureTest {

    @Autowired private DemoSeedService demoSeedService;
    @Autowired private GdprService gdprService;
    @Autowired private AthleteAccountService accountService;
    @Autowired private AthleteRepository athleteRepository;
    @Autowired private AthleteAccountRepository accountRepository;
    @Autowired private UserRepository userRepository;

    private User user;
    private UUID athleteId;

    @BeforeEach
    void setUp() {
        demoSeedService.seed();
        user = userRepository.findByEmailIgnoreCase(DemoSeedService.ATHLETE_EMAIL).orElseThrow();
        athleteId = user.getAthlete().getId();
    }

    @Test
    void invitedAthleteGetsAnAccountFromTheirFiche() {
        assertThat(accountRepository.findByUserId(user.getId())).isEmpty();

        var account = accountService.require(user.getId());

        assertThat(accountRepository.findByUserId(user.getId())).isPresent();
        assertThat(account.getFirstName()).isEqualTo(user.getAthlete().getFirstName());
        assertThat(athleteRepository.findById(athleteId).orElseThrow().getAccount().getId())
                .isEqualTo(account.getId());
    }

    @Test
    void deletingTheAccountErasesThePersonEverywhere() {
        var account = accountService.require(user.getId());
        // La fiche restée chez un ancien coach, reliée à la personne par son compte.
        Athlete current = athleteRepository.findById(athleteId).orElseThrow();
        Athlete former = new Athlete();
        former.setClub(current.getClub());
        former.setFirstName(current.getFirstName());
        former.setLastName(current.getLastName());
        former.setAccount(account);
        UUID formerId = athleteRepository.saveAndFlush(former).getId();

        gdprService.deleteAthleteData(athleteId);

        assertThat(athleteRepository.findById(athleteId)).isEmpty();
        assertThat(athleteRepository.findById(formerId)).isEmpty();
        assertThat(userRepository.findById(user.getId())).isEmpty();
        assertThat(accountRepository.findById(account.getId())).isEmpty();
    }
}
