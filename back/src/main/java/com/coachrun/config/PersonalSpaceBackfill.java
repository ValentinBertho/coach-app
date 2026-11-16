package com.coachrun.config;

import com.coachrun.entity.AthleteAccount;
import com.coachrun.entity.User;
import com.coachrun.repository.AthleteAccountRepository;
import com.coachrun.service.PersonalSpaceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Donne un espace personnel aux comptes athlètes inscrits avant qu'il existe.
 *
 * <p>Concerne les athlètes sans coach — inscrits et jamais acceptés, ou dont le coaching s'est
 * terminé : leur compte n'avait aucune fiche, donc aucun calendrier. Idempotent : un compte déjà
 * rattaché à une fiche (personnelle ou chez un coach) n'est pas touché.</p>
 */
@Slf4j
@Component
@Order(60)
@RequiredArgsConstructor
public class PersonalSpaceBackfill implements ApplicationRunner {

    private final AthleteAccountRepository accountRepository;
    private final PersonalSpaceService personalSpace;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        int created = 0;
        for (AthleteAccount account : accountRepository.findAll()) {
            User user = account.getUser();
            if (user == null || user.getAthlete() != null) {
                continue;
            }
            personalSpace.attach(user, account);
            created++;
        }
        if (created > 0) {
            log.info("Espaces personnels : {} compte(s) athlète rattaché(s).", created);
        }
    }
}
