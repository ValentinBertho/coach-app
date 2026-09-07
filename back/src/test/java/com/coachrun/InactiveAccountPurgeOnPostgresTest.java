package com.coachrun;

import com.coachrun.entity.Club;
import com.coachrun.entity.User;
import com.coachrun.entity.enums.UserRole;
import com.coachrun.entity.enums.UserStatus;
import com.coachrun.repository.ClubRepository;
import com.coachrun.repository.UserRepository;
import com.coachrun.service.InactiveAccountService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Les deux requêtes de la purge des comptes inactifs, sur un <b>vrai</b> PostgreSQL.
 *
 * <p><b>Pourquoi ce test existe à part.</b> La suite tourne sur H2 en mode PostgreSQL, ce qui
 * suffit presque toujours — mais pas pour ces deux requêtes-ci. Elles cumulent tout ce que H2
 * traite plus souplement que PostgreSQL : un {@code coalesce} à trois arguments sur des colonnes
 * <i>nullables</i>, réutilisé à la fois en filtre et en tri, et comparé à des paramètres
 * {@code Instant}. L'écart entre les deux moteurs a déjà coûté deux écrans partis en 500 en
 * production — la messagerie, puis le journal d'audit — avec une suite entièrement verte les
 * deux fois. Et ici l'enjeu n'est pas un écran : une requête qui échoue en silence, c'est une
 * durée de conservation annoncée qui cesse d'être appliquée.</p>
 *
 * <p>Ignoré sauf si {@code -Dpgtest=true} est passé (et qu'un PostgreSQL écoute) : la suite
 * ordinaire ne doit pas dépendre d'un serveur externe.</p>
 */
@SpringBootTest
@ActiveProfiles("pgtest")
@EnabledIfSystemProperty(named = "pgtest", matches = "true")
@Transactional
class InactiveAccountPurgeOnPostgresTest {

    private static final int RETENTION_DAYS = 730;
    private static final int NOTICE_DAYS = 30;

    @Autowired private InactiveAccountService accounts;
    @Autowired private UserRepository userRepository;
    @Autowired private ClubRepository clubRepository;

    private final Instant now = Instant.parse("2026-09-07T04:20:00Z");

    /** Le filtre et le tri, avec la colonne principale renseignée. */
    @Test
    void theWarningQueryRunsOnPostgres() {
        User dormant = coach(now.minus(Duration.ofDays(RETENTION_DAYS - 10)), null);
        coach(now.minus(Duration.ofDays(10)), null);

        var toWarn = accounts.findToWarn(now, RETENTION_DAYS, NOTICE_DAYS, 100);

        assertThat(toWarn).contains(dormant.getId());
    }

    /**
     * Le cas que H2 laisse passer sans rien dire : les deux premières colonnes du
     * {@code coalesce} sont nulles, et c'est la date de création qui décide.
     */
    @Test
    void theFallbackOnCreationDateRunsOnPostgres() {
        User neverSeen = coach(null, null);

        var toWarn = accounts.findToWarn(now, RETENTION_DAYS, NOTICE_DAYS, 100);

        assertThat(toWarn)
                .as("créé à l'instant : le repli sur created_at le protège")
                .doesNotContain(neverSeen.getId());
    }

    /** La requête de suppression, avec ses trois conditions sur la date de préavis. */
    @Test
    void thePurgeQueryRunsOnPostgres() {
        User due = coach(now.minus(Duration.ofDays(RETENTION_DAYS + 100)),
                now.minus(Duration.ofDays(NOTICE_DAYS + 1)));
        User warnedTooRecently = coach(now.minus(Duration.ofDays(RETENTION_DAYS + 100)),
                now.minus(Duration.ofDays(NOTICE_DAYS - 1)));
        User backAgain = coach(now.minus(Duration.ofDays(1)),
                now.minus(Duration.ofDays(NOTICE_DAYS + 1)));

        var toPurge = accounts.findToPurge(now, RETENTION_DAYS, NOTICE_DAYS, 100);

        assertThat(toPurge).contains(due.getId());
        assertThat(toPurge).doesNotContain(warnedTooRecently.getId(), backAgain.getId());
    }

    /** Le décompte qui protège un club de perdre son dernier encadrant. */
    @Test
    void theClubMemberCountRunsOnPostgres() {
        Club club = club();
        User first = coach(club, null, null);
        User second = coach(club, null, null);

        assertThat(userRepository.countOtherMembersOfClub(club.getId(), first.getId()))
                .isEqualTo(1);
        assertThat(userRepository.countOtherMembersOfClub(UUID.randomUUID(), second.getId()))
                .isZero();
    }

    private User coach(Instant lastSeenAt, Instant warnedAt) {
        return coach(club(), lastSeenAt, warnedAt);
    }

    private User coach(Club club, Instant lastSeenAt, Instant warnedAt) {
        User user = new User();
        user.setClub(club);
        user.setRole(UserRole.HEAD_COACH);
        user.setStatus(UserStatus.ACTIVE);
        user.setFullName("Coach");
        user.setEmail("pg-inactif-" + UUID.randomUUID() + "@test.fr");
        user.setLastSeenAt(lastSeenAt);
        user.setInactivityWarnedAt(warnedAt);
        return userRepository.saveAndFlush(user);
    }

    private Club club() {
        Club club = new Club();
        club.setName("PG AC");
        club.setSlug("pg-" + UUID.randomUUID());
        return clubRepository.saveAndFlush(club);
    }
}
