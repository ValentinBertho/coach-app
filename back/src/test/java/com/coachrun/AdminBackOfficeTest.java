package com.coachrun;

import com.coachrun.entity.Athlete;
import com.coachrun.entity.Club;
import com.coachrun.entity.User;
import com.coachrun.entity.enums.AdminAuditAction;
import com.coachrun.entity.enums.AthleteStatus;
import com.coachrun.entity.enums.ClubStatus;
import com.coachrun.entity.enums.UserRole;
import com.coachrun.entity.enums.UserStatus;
import com.coachrun.repository.AdminAuditLogRepository;
import com.coachrun.repository.AthleteRepository;
import com.coachrun.repository.ClubRepository;
import com.coachrun.repository.UserRepository;
import com.coachrun.security.JwtService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Back-office d'administration : pilotage, recherche, fiches, actions de support, journal d'audit.
 *
 * <p>Les cas les plus importants ici ne sont pas les chemins nominaux mais les <b>refus</b> :
 * chacun correspond à un accident dont on ne se relève pas depuis l'application — perdre le
 * dernier administrateur, se démettre soi-même, se supprimer.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
class AdminBackOfficeTest {

    @Autowired
    private WebApplicationContext context;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private ClubRepository clubRepository;
    @Autowired
    private AthleteRepository athleteRepository;
    @Autowired
    private AdminAuditLogRepository auditRepository;
    @Autowired
    private JwtService jwtService;

    private MockMvc mockMvc() {
        return MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    private User newAdmin() {
        User admin = new User();
        admin.setEmail("bo-admin-" + UUID.randomUUID() + "@test.fr");
        admin.setFullName("Admin Back-office");
        admin.setRole(UserRole.PLATFORM_ADMIN);
        admin.setStatus(UserStatus.ACTIVE);
        return userRepository.save(admin);
    }

    private String tokenOf(User user) {
        return jwtService.generateAccessToken(user);
    }

    private Club newClub(String name) {
        Club club = new Club();
        club.setName(name);
        club.setSlug("bo-" + UUID.randomUUID());
        club.setStatus(ClubStatus.ACTIVE);
        return clubRepository.save(club);
    }

    private User newCoach(Club club) {
        User coach = new User();
        coach.setEmail("bo-coach-" + UUID.randomUUID() + "@test.fr");
        coach.setFullName("Coach Back-office");
        coach.setRole(UserRole.COACH);
        coach.setStatus(UserStatus.ACTIVE);
        coach.setPasswordHash("{noop}x");
        coach.setClub(club);
        return userRepository.save(coach);
    }

    private String registerCoachToken(MockMvc mvc) throws Exception {
        String body = """
                {"email":"bo-%s@test.fr","password":"password123","fullName":"C","termsAccepted": true, "clubName":"BO %s"}
                """.formatted(UUID.randomUUID(), UUID.randomUUID());
        JsonNode auth = objectMapper.readTree(mvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andReturn().getResponse().getContentAsString());
        return auth.get("accessToken").asText();
    }

    // ------------------------------------------------------------------
    // Accès
    // ------------------------------------------------------------------

    @Test
    void everyNewAdminRouteRefusesANonAdmin() throws Exception {
        MockMvc mvc = mockMvc();
        String coachToken = registerCoachToken(mvc);
        // Une route ajoutée sans @PreAuthorize passerait inaperçue : on les balaie toutes.
        for (String path : new String[]{
                "/admin/overview", "/admin/search?q=ab", "/admin/platform", "/admin/audit",
                "/admin/audit/actions"}) {
            mvc.perform(get(path).header("Authorization", "Bearer " + coachToken))
                    .andExpect(status().isForbidden());
        }
        mvc.perform(get("/admin/overview")).andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------------------------
    // Pilotage & recherche
    // ------------------------------------------------------------------

    @Test
    void overviewRendersCountsSignalsAndIntegrations() throws Exception {
        MockMvc mvc = mockMvc();
        String token = tokenOf(newAdmin());
        mvc.perform(get("/admin/overview").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.counts.clubs").exists())
                .andExpect(jsonPath("$.growth.newUsers30d").exists())
                .andExpect(jsonPath("$.engagement.activeUsers7d").exists())
                .andExpect(jsonPath("$.integrations[0].key").exists())
                .andExpect(jsonPath("$.signals").isArray());
    }

    @Test
    void globalSearchFindsAUserAndAClubInOneCall() throws Exception {
        MockMvc mvc = mockMvc();
        String token = tokenOf(newAdmin());
        Club club = newClub("Foulées Zeta " + UUID.randomUUID());
        User coach = newCoach(club);

        mvc.perform(get("/admin/search").param("q", coach.getEmail())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.users[0].id").value(coach.getId().toString()))
                .andExpect(jsonPath("$.users[0].route").value("/admin/users/" + coach.getId()));

        mvc.perform(get("/admin/search").param("q", "Foulées Zeta")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clubs[0].id").value(club.getId().toString()));
    }

    @Test
    void searchStaysSilentBelowTwoCharacters() throws Exception {
        // Une lettre ramènerait une bonne partie de la base : ce n'est pas une recherche.
        MockMvc mvc = mockMvc();
        String token = tokenOf(newAdmin());
        mvc.perform(get("/admin/search").param("q", "a").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.users").isEmpty())
                .andExpect(jsonPath("$.clubs").isEmpty());
    }

    @Test
    void platformNeverExposesASecretValue() throws Exception {
        MockMvc mvc = mockMvc();
        String token = tokenOf(newAdmin());
        String body = mvc.perform(get("/admin/platform").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.settings[0].key").exists())
                .andReturn().getResponse().getContentAsString();

        // La réponse nomme les variables d'environnement ; elle ne doit jamais porter de valeur.
        assertThat(body.toLowerCase())
                .doesNotContain("client-secret")
                .doesNotContain("clientsecret")
                .doesNotContain("private-key")
                .doesNotContain("privatekey")
                .doesNotContain("verify-token")
                .doesNotContain("verifytoken");
    }

    // ------------------------------------------------------------------
    // Garde-fous : ne jamais fermer le back-office sur soi-même
    // ------------------------------------------------------------------

    @Test
    void anAdminCannotChangeTheirOwnRole() throws Exception {
        MockMvc mvc = mockMvc();
        User admin = newAdmin();
        mvc.perform(put("/admin/users/" + admin.getId())
                        .header("Authorization", "Bearer " + tokenOf(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"COACH\"}"))
                .andExpect(status().isForbidden());
        assertThat(userRepository.findById(admin.getId()).orElseThrow().getRole())
                .isEqualTo(UserRole.PLATFORM_ADMIN);
    }

    @Test
    void anAdminCannotSuspendOrDeleteTheirOwnAccount() throws Exception {
        MockMvc mvc = mockMvc();
        User admin = newAdmin();
        String token = tokenOf(admin);

        mvc.perform(post("/admin/users/" + admin.getId() + "/suspend")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/admin/users/" + admin.getId()).header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
        assertThat(userRepository.existsById(admin.getId())).isTrue();
    }

    /**
     * Le dernier administrateur actif ne peut être ni suspendu ni supprimé.
     *
     * <p><b>Ce test emprunte l'état de toute la base, et doit le rendre.</b> La classe n'est pas
     * transactionnelle : ce qu'elle écrit est commité pour de bon dans la base H2 partagée par
     * les 137 classes de la suite. Or il faut, le temps de l'assertion, n'avoir qu'un seul
     * administrateur actif — donc suspendre tous les autres, y compris
     * {@code admin@coachrun.fr} du jeu de démo dès lors qu'une classe non transactionnelle l'a
     * commité auparavant.</p>
     *
     * <p>Sans la remise en état ci-dessous, cette suspension survivait au test et empoisonnait
     * toute classe ultérieure se connectant comme administrateur de démo : {@code MailLogTest}
     * prenait quatre 401 « Ce compte est suspendu » dans son {@code setUp}, à des centaines de
     * lignes de journal de la cause. Le défaut ne se voyait que dans l'ordre d'exécution de la
     * CI, jamais en local.</p>
     */
    @Test
    void theLastActiveAdminCannotBeRemoved() throws Exception {
        MockMvc mvc = mockMvc();
        // Base de test partagée : on la ramène volontairement à un seul administrateur actif,
        // qui est exactement la situation que la garde doit protéger.
        List<UUID> borrowed = userRepository.findAll().stream()
                .filter(u -> u.getRole() == UserRole.PLATFORM_ADMIN && u.getStatus() == UserStatus.ACTIVE)
                .map(u -> {
                    u.setStatus(UserStatus.SUSPENDED);
                    userRepository.save(u);
                    return u.getId();
                })
                .toList();

        try {
            User last = newAdmin();
            // L'acteur est un administrateur suspendu : il conserve le rôle porté par son jeton mais
            // ne compte pas parmi les administrateurs actifs, ce qui isole le cas testé.
            User caller = newAdmin();
            caller.setStatus(UserStatus.SUSPENDED);
            userRepository.save(caller);
            String token = tokenOf(caller);

            mvc.perform(post("/admin/users/" + last.getId() + "/suspend")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isConflict());
            mvc.perform(delete("/admin/users/" + last.getId()).header("Authorization", "Bearer " + token))
                    .andExpect(status().isConflict());

            assertThat(userRepository.findById(last.getId()).orElseThrow().getStatus())
                    .isEqualTo(UserStatus.ACTIVE);
        } finally {
            // Rendu même sur échec : un test qui casse ne doit pas en faire tomber cinq autres
            // pour une raison sans rapport avec ce qu'ils vérifient.
            userRepository.findAllById(borrowed).forEach(u -> {
                u.setStatus(UserStatus.ACTIVE);
                userRepository.save(u);
            });
        }
    }

    @Test
    void suspendingAnAccountAlsoClosesItsOpenSessions() throws Exception {
        MockMvc mvc = mockMvc();
        User admin = newAdmin();
        Club club = newClub("Club suspension " + UUID.randomUUID());
        User coach = newCoach(club);

        mvc.perform(post("/admin/users/" + coach.getId() + "/suspend")
                        .header("Authorization", "Bearer " + tokenOf(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Compte compromis\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUSPENDED"));

        User reloaded = userRepository.findById(coach.getId()).orElseThrow();
        // Sans cette date, le jeton d'accès émis avant la suspension resterait valable, et le
        // rafraîchissement continuerait pendant trente jours.
        assertThat(reloaded.getSessionsInvalidatedAt()).isNotNull();
    }

    // ------------------------------------------------------------------
    // Journal d'audit
    // ------------------------------------------------------------------

    @Test
    void everySensitiveActionLeavesAnAuditEntryNamingItsActor() throws Exception {
        MockMvc mvc = mockMvc();
        User admin = newAdmin();
        String token = tokenOf(admin);
        Club club = newClub("Club audité " + UUID.randomUUID());
        User coach = newCoach(club);

        mvc.perform(post("/admin/users/" + coach.getId() + "/suspend")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        var entries = auditRepository.findTop20ByTargetIdOrderByOccurredAtDesc(coach.getId());
        assertThat(entries).isNotEmpty();
        assertThat(entries.get(0).getAction()).isEqualTo(AdminAuditAction.USER_SUSPENDED);
        assertThat(entries.get(0).getActorUserId()).isEqualTo(admin.getId());
        // L'e-mail est recopié : la trace doit rester lisible si l'acteur est supprimé plus tard.
        assertThat(entries.get(0).getActorEmail()).isEqualTo(admin.getEmail());
    }

    @Test
    void deletingAUserIsRecordedBeforeTheRowDisappears() throws Exception {
        MockMvc mvc = mockMvc();
        User admin = newAdmin();
        Club club = newClub("Club supprimé " + UUID.randomUUID());
        User coach = newCoach(club);
        UUID coachId = coach.getId();
        String coachEmail = coach.getEmail();

        mvc.perform(delete("/admin/users/" + coachId).header("Authorization", "Bearer " + tokenOf(admin)))
                .andExpect(status().isNoContent());

        assertThat(userRepository.existsById(coachId)).isFalse();
        var entries = auditRepository.findTop20ByTargetIdOrderByOccurredAtDesc(coachId);
        assertThat(entries).anyMatch(e -> e.getAction() == AdminAuditAction.USER_DELETED
                && coachEmail.equals(e.getTargetLabel()));
    }

    @Test
    void theAuditJournalIsReadOnlyAndFilterable() throws Exception {
        MockMvc mvc = mockMvc();
        User admin = newAdmin();
        String token = tokenOf(admin);
        newClub("Club journal " + UUID.randomUUID());

        mvc.perform(post("/admin/clubs").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Club journalisé\"}"))
                .andExpect(status().isCreated());

        mvc.perform(get("/admin/audit").param("action", "CLUB_CREATED")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].action").value("CLUB_CREATED"))
                .andExpect(jsonPath("$.content[0].actionLabel").value("Club créé"));

        // Aucune route n'écrit dans le journal : un POST doit être refusé par le routage.
        mvc.perform(post("/admin/audit").header("Authorization", "Bearer " + token))
                .andExpect(status().is4xxClientError());
    }

    // ------------------------------------------------------------------
    // Fiches
    // ------------------------------------------------------------------

    @Test
    void userDetailCarriesWhatSupportNeeds() throws Exception {
        MockMvc mvc = mockMvc();
        String token = tokenOf(newAdmin());
        Club club = newClub("Club fiche " + UUID.randomUUID());
        User coach = newCoach(club);

        mvc.perform(get("/admin/users/" + coach.getId() + "/detail")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(coach.getEmail()))
                .andExpect(jsonPath("$.emailVerified").exists())
                .andExpect(jsonPath("$.realEmail").value(true))
                .andExpect(jsonPath("$.clubName").value(club.getName()))
                .andExpect(jsonPath("$.history").isArray());
    }

    @Test
    void clubDetailShowsWhatADeletionWouldDestroy() throws Exception {
        MockMvc mvc = mockMvc();
        String token = tokenOf(newAdmin());
        Club club = newClub("Club impact " + UUID.randomUUID());
        User coach = newCoach(club);

        mvc.perform(get("/admin/clubs/" + club.getId() + "/detail")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value(club.getName()))
                .andExpect(jsonPath("$.coaches").value(1))
                .andExpect(jsonPath("$.athletes").value(0))
                .andExpect(jsonPath("$.members[0].id").value(coach.getId().toString()))
                .andExpect(jsonPath("$.members[0].primaryClub").value(true));
    }

    @Test
    void multiClubAttachmentIsDrivableFromTheApi() throws Exception {
        // Les deux routes existaient sans aucun appelant : le multi-club de coach ne se pilotait
        // qu'en base.
        MockMvc mvc = mockMvc();
        String token = tokenOf(newAdmin());
        Club main = newClub("Club principal " + UUID.randomUUID());
        Club extra = newClub("Club additionnel " + UUID.randomUUID());
        User coach = newCoach(main);

        mvc.perform(put("/admin/users/" + coach.getId() + "/clubs/" + extra.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.additionalClubs[0].id").value(extra.getId().toString()));

        mvc.perform(put("/admin/users/" + coach.getId() + "/clubs/" + main.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict());

        mvc.perform(delete("/admin/users/" + coach.getId() + "/clubs/" + extra.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.additionalClubs").isEmpty());
    }

    @Test
    void theStatusFilterActuallyFilters() throws Exception {
        // Le contrôleur l'acceptait depuis toujours ; le front ne l'envoyait jamais.
        MockMvc mvc = mockMvc();
        User admin = newAdmin();
        String token = tokenOf(admin);
        Club club = newClub("Club filtre " + UUID.randomUUID());
        User coach = newCoach(club);

        mvc.perform(post("/admin/users/" + coach.getId() + "/suspend")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        String body = mvc.perform(get("/admin/users").param("status", "SUSPENDED").param("q", coach.getEmail())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(body).contains(coach.getEmail());

        String none = mvc.perform(get("/admin/users").param("status", "ACTIVE").param("q", coach.getEmail())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(none).doesNotContain(coach.getEmail());
    }

    // ------------------------------------------------------------------
    // Athlètes
    // ------------------------------------------------------------------

    /**
     * Deux défauts de l'écran d'édition, l'un silencieux et l'autre inerte.
     *
     * <p>Le formulaire ne portait pas la date de naissance alors que le serveur écrit ce qu'il
     * reçoit : <b>chaque enregistrement depuis l'administration effaçait la date de naissance</b>.
     * Et {@code AthleteRequest} n'avait pas de statut : archiver un athlète depuis cet écran
     * semblait fonctionner sans jamais rien changer.</p>
     */
    @Test
    void editingAnAthleteKeepsItsBirthDateAndAppliesTheStatus() throws Exception {
        MockMvc mvc = mockMvc();
        String token = tokenOf(newAdmin());
        Club club = newClub("Club athlète " + UUID.randomUUID());

        Athlete athlete = new Athlete();
        athlete.setClub(club);
        athlete.setFirstName("Léa");
        athlete.setLastName("Martin");
        athlete.setBirthDate(java.time.LocalDate.of(1994, 5, 12));
        athlete.setStatus(AthleteStatus.ACTIVE);
        athlete = athleteRepository.save(athlete);

        mvc.perform(put("/admin/athletes/" + athlete.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"firstName":"Léa","lastName":"Martin","birthDate":"1994-05-12",
                                 "status":"ARCHIVED"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.birthDate").value("1994-05-12"))
                .andExpect(jsonPath("$.status").value("ARCHIVED"));

        Athlete reloaded = athleteRepository.findById(athlete.getId()).orElseThrow();
        assertThat(reloaded.getBirthDate()).isEqualTo(java.time.LocalDate.of(1994, 5, 12));
        assertThat(reloaded.getStatus()).isEqualTo(AthleteStatus.ARCHIVED);
    }

    /** Statut absent = statut inchangé : tous les appelants antérieurs ne l'envoient pas. */
    @Test
    void anAbsentStatusLeavesTheAthleteUntouched() throws Exception {
        MockMvc mvc = mockMvc();
        String token = tokenOf(newAdmin());
        Club club = newClub("Club statut " + UUID.randomUUID());

        Athlete athlete = new Athlete();
        athlete.setClub(club);
        athlete.setFirstName("Paul");
        athlete.setLastName("Durand");
        athlete.setStatus(AthleteStatus.PAUSED);
        athlete = athleteRepository.save(athlete);

        mvc.perform(put("/admin/athletes/" + athlete.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"Paul\",\"lastName\":\"Durand\"}"))
                .andExpect(status().isOk());

        assertThat(athleteRepository.findById(athlete.getId()).orElseThrow().getStatus())
                .isEqualTo(AthleteStatus.PAUSED);
    }

    /** Le journal dit qu'une donnée physiologique a bougé — jamais sa valeur. */
    @Test
    void theAuditNeverCarriesAPhysiologicalValue() throws Exception {
        MockMvc mvc = mockMvc();
        String token = tokenOf(newAdmin());
        Club club = newClub("Club physio " + UUID.randomUUID());

        Athlete athlete = new Athlete();
        athlete.setClub(club);
        athlete.setFirstName("Sara");
        athlete.setLastName("Blanc");
        athlete.setStatus(AthleteStatus.ACTIVE);
        athlete = athleteRepository.save(athlete);

        mvc.perform(put("/admin/athletes/" + athlete.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"firstName":"Sara","lastName":"Blanc","hrMax":191,"hrRest":44,
                                 "medicalNotes":"asthme d'effort"}
                                """))
                .andExpect(status().isOk());

        var entries = auditRepository.findTop20ByTargetIdOrderByOccurredAtDesc(athlete.getId());
        assertThat(entries).isNotEmpty();
        String summary = entries.get(0).getSummary();
        assertThat(summary).contains("données physiologiques");
        assertThat(summary).doesNotContain("191").doesNotContain("44").doesNotContain("asthme");
    }
}
