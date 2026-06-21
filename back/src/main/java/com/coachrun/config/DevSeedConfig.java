package com.coachrun.config;

import com.coachrun.entity.Club;
import com.coachrun.entity.User;
import com.coachrun.entity.enums.ClubStatus;
import com.coachrun.entity.enums.UserRole;
import com.coachrun.entity.enums.UserStatus;
import com.coachrun.repository.ClubRepository;
import com.coachrun.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Données de démo en profil dev : un coach connectable immédiatement.
 * Identifiants : demo@coachrun.fr / password123
 */
@Slf4j
@Component
@Profile("dev")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.seed.enabled", havingValue = "true", matchIfMissing = true)
public class DevSeedConfig implements CommandLineRunner {

    private static final String DEMO_EMAIL = "demo@coachrun.fr";

    private final UserRepository userRepository;
    private final ClubRepository clubRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(String... args) {
        if (userRepository.existsByEmailIgnoreCase(DEMO_EMAIL)) {
            return;
        }

        Club club = new Club();
        club.setName("Club Démo");
        club.setSlug("club-demo");
        club.setStatus(ClubStatus.ACTIVE);
        club = clubRepository.save(club);

        User coach = new User();
        coach.setEmail(DEMO_EMAIL);
        coach.setPasswordHash(passwordEncoder.encode("password123"));
        coach.setFullName("Coach Démo");
        coach.setRole(UserRole.HEAD_COACH);
        coach.setStatus(UserStatus.ACTIVE);
        coach.setClub(club);
        userRepository.save(coach);

        log.info("[seed dev] Coach de démo créé : {} / password123", DEMO_EMAIL);
    }
}
