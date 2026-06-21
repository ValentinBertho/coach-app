package com.coachrun;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Vérifie que le contexte Spring démarre et que les migrations Liquibase s'appliquent
 * (profil test = H2 mode PostgreSQL). Valide la chaîne entité ↔ Liquibase ↔ JPA.
 */
@SpringBootTest
@ActiveProfiles("test")
class CoachRunApplicationTests {

    @Test
    void contextLoads() {
    }
}
