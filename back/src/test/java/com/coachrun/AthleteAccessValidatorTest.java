package com.coachrun;

import com.coachrun.entity.Athlete;
import com.coachrun.entity.AthleteCoachPermission;
import com.coachrun.entity.Club;
import com.coachrun.entity.ClubMember;
import com.coachrun.entity.CoachAthleteRelation;
import com.coachrun.entity.User;
import com.coachrun.entity.enums.ClubRole;
import com.coachrun.entity.enums.PermissionLevel;
import com.coachrun.entity.enums.UserRole;
import com.coachrun.repository.AthleteCoachPermissionRepository;
import com.coachrun.repository.AthleteRepository;
import com.coachrun.repository.ClubMemberRepository;
import com.coachrun.repository.ClubRepository;
import com.coachrun.repository.CoachAthleteRelationRepository;
import com.coachrun.repository.UserRepository;
import com.coachrun.security.AthleteAccessValidator;
import com.coachrun.security.AuthPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Couvre les règles d'accès gradué coach↔athlète (équivalent applicatif des policies RLS DARI Lab) :
 * référent, permission explicite, lecture par défaut owner/principal, et étanchéité des athlètes privés.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AthleteAccessValidatorTest {

    @Autowired private AthleteAccessValidator validator;
    @Autowired private ClubRepository clubRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private AthleteRepository athleteRepository;
    @Autowired private ClubMemberRepository clubMemberRepository;
    @Autowired private CoachAthleteRelationRepository relationRepository;
    @Autowired private AthleteCoachPermissionRepository permissionRepository;

    private Club club;
    private Club otherClub;
    private User referentCoach;
    private User ownerCoach;
    private User assistantCoach;
    private User outsiderCoach;
    private Athlete clubAthlete;
    private Athlete privateAthlete;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        club = clubRepository.save(newClub("Club " + suffix, "club-" + suffix));
        otherClub = clubRepository.save(newClub("Other " + suffix, "other-" + suffix));

        referentCoach = userRepository.save(newCoach("referent-" + suffix + "@t.io"));
        ownerCoach = userRepository.save(newCoach("owner-" + suffix + "@t.io"));
        assistantCoach = userRepository.save(newCoach("assistant-" + suffix + "@t.io"));
        // outsiderCoach : coach d'un AUTRE club (aucun accès tenant à ce club).
        outsiderCoach = userRepository.save(newCoachInClub("outsider-" + suffix + "@t.io", otherClub));

        clubMemberRepository.save(member(ownerCoach, ClubRole.OWNER));
        clubMemberRepository.save(member(referentCoach, ClubRole.COACH_PRINCIPAL));
        clubMemberRepository.save(member(assistantCoach, ClubRole.COACH_ASSISTANT));
        // outsiderCoach : volontairement non membre du club.

        clubAthlete = athleteRepository.save(newAthlete("Club", "Athlete"));
        privateAthlete = athleteRepository.save(newAthlete("Private", "Athlete"));

        // Référent sur les deux athlètes ; clubAthlete rattaché au club, privateAthlete privé.
        relationRepository.save(relation(referentCoach, clubAthlete, club));
        relationRepository.save(relation(referentCoach, privateAthlete, null));

        // L'assistant a une permission COMMENT sur l'athlète club, et (à tort) sur le privé.
        permissionRepository.save(permission(assistantCoach, clubAthlete, PermissionLevel.COMMENT, null));
        permissionRepository.save(permission(assistantCoach, privateAthlete, PermissionLevel.WRITE, null));
    }

    @Test
    void referentHasFullWriteAccessIncludingPrivate() {
        assertThat(validator.effectiveLevel(referentCoach.getId(), clubAthlete.getId()))
                .contains(PermissionLevel.WRITE);
        assertThat(validator.effectiveLevel(referentCoach.getId(), privateAthlete.getId()))
                .contains(PermissionLevel.WRITE);
    }

    @Test
    void ownerGetsReadByDefaultOnClubAthleteButNeverOnPrivate() {
        assertThat(validator.effectiveLevel(ownerCoach.getId(), clubAthlete.getId()))
                .contains(PermissionLevel.READ);
        // Athlète privé : invisible même pour l'Owner.
        assertThat(validator.effectiveLevel(ownerCoach.getId(), privateAthlete.getId())).isEmpty();
    }

    @Test
    void explicitPermissionGrantsItsLevelOnClubAthlete() {
        assertThat(validator.effectiveLevel(assistantCoach.getId(), clubAthlete.getId()))
                .contains(PermissionLevel.COMMENT);
    }

    @Test
    void privateAthleteNeverSharedEvenWithExplicitPermission() {
        // Bien qu'une permission WRITE existe, l'athlète privé reste étanche.
        assertThat(validator.effectiveLevel(assistantCoach.getId(), privateAthlete.getId())).isEmpty();
    }

    @Test
    void coachOfAnotherClubHasNoAccess() {
        assertThat(validator.effectiveLevel(outsiderCoach.getId(), clubAthlete.getId())).isEmpty();
    }

    @Test
    void expiredPermissionIsIgnored() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        // Coach d'un autre club avec une permission COMMENT expirée hier ⇒ aucun accès.
        User tempCoach = userRepository.save(newCoachInClub("temp-" + suffix + "@t.io", otherClub));
        permissionRepository.save(permission(tempCoach, clubAthlete, PermissionLevel.COMMENT,
                Instant.now().minus(1, ChronoUnit.DAYS)));
        assertThat(validator.effectiveLevel(tempCoach.getId(), clubAthlete.getId())).isEmpty();
    }

    @Test
    void booleanHelpersRespectHierarchy() {
        Authentication assistant = auth(assistantCoach);
        assertThat(validator.canRead(assistant, clubAthlete.getId())).isTrue();
        assertThat(validator.canComment(assistant, clubAthlete.getId())).isTrue();
        assertThat(validator.canWrite(assistant, clubAthlete.getId())).isFalse();

        Authentication owner = auth(ownerCoach);
        assertThat(validator.canRead(owner, clubAthlete.getId())).isTrue();
        assertThat(validator.canComment(owner, clubAthlete.getId())).isFalse();
    }

    @Test
    void platformAdminHasTransverseAccess() {
        AuthPrincipal admin = new AuthPrincipal(
                UUID.randomUUID(), null, null, "admin@t.io", UserRole.PLATFORM_ADMIN);
        Authentication auth = new UsernamePasswordAuthenticationToken(admin, null, List.of());
        assertThat(validator.canWrite(auth, privateAthlete.getId())).isTrue();
    }

    @Test
    void athleteAccountHasNoCoachAccess() {
        AuthPrincipal athlete = new AuthPrincipal(
                UUID.randomUUID(), club.getId(), UUID.randomUUID(), "ath@t.io", UserRole.ATHLETE);
        Authentication auth = new UsernamePasswordAuthenticationToken(athlete, null, List.of());
        assertThat(validator.canRead(auth, clubAthlete.getId())).isFalse();
    }

    // --- Fabriques de fixtures ------------------------------------------------

    private Club newClub(String name, String slug) {
        Club c = new Club();
        c.setName(name);
        c.setSlug(slug);
        return c;
    }

    private User newCoach(String email) {
        return newCoachInClub(email, club);
    }

    private User newCoachInClub(String email, Club homeClub) {
        User u = new User();
        u.setEmail(email);
        u.setFullName("Coach " + email);
        u.setRole(UserRole.COACH);
        u.setClub(homeClub);
        return u;
    }

    private Athlete newAthlete(String first, String last) {
        Athlete a = new Athlete();
        a.setClub(club);
        a.setFirstName(first);
        a.setLastName(last);
        return a;
    }

    private ClubMember member(User coach, ClubRole role) {
        ClubMember m = new ClubMember();
        m.setClub(club);
        m.setCoach(coach);
        m.setClubRole(role);
        return m;
    }

    private CoachAthleteRelation relation(User coach, Athlete athlete, Club relClub) {
        CoachAthleteRelation r = new CoachAthleteRelation();
        r.setCoach(coach);
        r.setAthlete(athlete);
        r.setClub(relClub);
        r.setReferent(true);
        return r;
    }

    private AthleteCoachPermission permission(User coach, Athlete athlete, PermissionLevel level, Instant expiresAt) {
        AthleteCoachPermission p = new AthleteCoachPermission();
        p.setCoach(coach);
        p.setAthlete(athlete);
        p.setPermission(level);
        p.setExpiresAt(expiresAt);
        return p;
    }

    private Authentication auth(User coach) {
        AuthPrincipal p = new AuthPrincipal(coach.getId(),
                coach.getClub() == null ? null : coach.getClub().getId(),
                null, coach.getEmail(), coach.getRole());
        return new UsernamePasswordAuthenticationToken(p, null, List.of());
    }
}
