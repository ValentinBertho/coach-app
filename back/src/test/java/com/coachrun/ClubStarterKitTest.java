package com.coachrun;

import com.coachrun.entity.Club;
import com.coachrun.entity.WorkoutTemplate;
import com.coachrun.entity.enums.CategoryDomain;
import com.coachrun.entity.enums.ClubStatus;
import com.coachrun.repository.ClubMemberRepository;
import com.coachrun.repository.ClubRepository;
import com.coachrun.repository.RunDrillRepository;
import com.coachrun.repository.SessionCategoryRepository;
import com.coachrun.repository.TrainingZoneRepository;
import com.coachrun.repository.UserRepository;
import com.coachrun.repository.WorkoutTemplateRepository;
import com.coachrun.repository.ZoneSetRepository;
import com.coachrun.service.ClubStarterKitService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Le jeu de départ d'un club — ce qu'un coach trouve le jour de son inscription.
 *
 * <p>La création de compte posait un club, un utilisateur et une adhésion, rien d'autre : le coach
 * ouvrait une bibliothèque vide et une liste de catégories vide, et sa première tâche était
 * d'écrire lui-même les six séances les plus banales de la course à pied.</p>
 *
 * <p><b>Non transactionnel, à dessein.</b> L'installation s'exécute en {@code REQUIRES_NEW} —
 * parce qu'elle part d'un {@code afterCommit} de l'inscription, où une propagation {@code REQUIRED}
 * rejoindrait une transaction déjà committée et perdrait toutes ses écritures sans lever la
 * moindre exception. Une transaction de test englobante rendrait ce comportement invisible : elle
 * cacherait le club au service, et surtout elle masquerait exactement le défaut qu'on veut garder
 * attrapé. Les clubs créés ici sont donc commités pour de bon, et rangés en {@link #cleanUp()}.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
class ClubStarterKitTest {

    @Autowired private ClubStarterKitService starterKit;
    @Autowired private ClubRepository clubRepository;
    @Autowired private SessionCategoryRepository categoryRepository;
    @Autowired private WorkoutTemplateRepository templateRepository;
    @Autowired private RunDrillRepository drillRepository;
    @Autowired private TrainingZoneRepository zoneRepository;
    @Autowired private ZoneSetRepository zoneSetRepository;
    @Autowired private ClubMemberRepository memberRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private WebApplicationContext context;
    @Autowired private ObjectMapper objectMapper;

    /** Tout ce que ce test a commité, à défaire une fois le cas passé. */
    private final List<UUID> createdClubs = new ArrayList<>();
    private final List<String> createdEmails = new ArrayList<>();

    private UUID freshClub() {
        Club club = new Club();
        club.setName("Club neuf " + UUID.randomUUID());
        club.setSlug("club-neuf-" + UUID.randomUUID());
        club.setStatus(ClubStatus.ACTIVE);
        UUID id = clubRepository.save(club).getId();
        createdClubs.add(id);
        return id;
    }

    private List<WorkoutTemplate> templates(UUID clubId) {
        return templateRepository.findByClubId(clubId, PageRequest.of(0, 50)).getContent();
    }

    @AfterEach
    void cleanUp() {
        for (String email : createdEmails) {
            userRepository.findByEmailIgnoreCase(email).ifPresent(u ->
                    memberRepository.findByCoachIdAndActiveTrue(u.getId())
                            .forEach(memberRepository::delete));
            userRepository.findByEmailIgnoreCase(email).ifPresent(userRepository::delete);
        }
        for (UUID clubId : createdClubs) {
            templateRepository.deleteAll(templates(clubId));
            drillRepository.deleteAll(drillRepository.findByClubIdOrderByCategoryAscNameAsc(clubId));
            categoryRepository.deleteAll(categoryRepository
                    .findByClubIdAndDomainOrderBySortOrderAscNameAsc(clubId, CategoryDomain.COURSE));
            zoneRepository.deleteAll(zoneRepository.findByClubIdOrderBySortOrderAscNameAsc(clubId));
            zoneSetRepository.deleteAll(zoneSetRepository.findByClubIdOrderBySortOrderAscNameAsc(clubId));
            clubRepository.deleteById(clubId);
        }
        createdEmails.clear();
        createdClubs.clear();
    }

    @Test
    void aNewClubGetsCategoriesDrillsAndSessions() {
        UUID clubId = freshClub();

        assertThat(starterKit.install(clubId)).isTrue();

        assertThat(categoryRepository.findByClubIdAndDomainOrderBySortOrderAscNameAsc(
                clubId, CategoryDomain.COURSE)).hasSize(6);
        assertThat(drillRepository.findByClubIdOrderByCategoryAscNameAsc(clubId)).hasSize(6);
        assertThat(templates(clubId)).hasSize(11);
    }

    /**
     * Les séances prescrivent <b>par zone</b> : c'est ce qui leur permet de se calibrer seules sur
     * chaque athlète dès que ses références physiologiques sont saisies. Une bibliothèque
     * d'exemple en allures figées n'apprendrait rien du produit.
     */
    @Test
    void sessionsArePrescribedByZoneSoTheyCalibrateThemselves() {
        UUID clubId = freshClub();
        starterKit.install(clubId);

        // Le seed de zones est déclenché par le jeu de départ, sans attendre la première lecture.
        assertThat(zoneRepository.existsByClubId(clubId)).isTrue();

        assertThat(templates(clubId)).allSatisfy(t -> {
            assertThat(t.getStructureJson()).contains("zoneId");
            // Une séance sans catégorie retombe dans une liste à plat : le rangement fait partie
            // du jeu de départ, il n'en est pas un ornement.
            assertThat(t.getCategory()).isNotNull();
            assertThat(t.getNotes()).isNotBlank();
        });
    }

    /**
     * Idempotent : rejouable sans doublon. C'est ce qui permet de l'appeler après coup sur un club
     * dont l'installation aurait échoué à l'inscription, sans avoir à vérifier quoi que ce soit.
     */
    @Test
    void installingTwiceLeavesTheLibraryUntouched() {
        UUID clubId = freshClub();
        starterKit.install(clubId);
        int templatesAfterFirst = templates(clubId).size();

        assertThat(starterKit.install(clubId)).isFalse();

        assertThat(templates(clubId)).hasSize(templatesAfterFirst);
        assertThat(drillRepository.findByClubIdOrderByCategoryAscNameAsc(clubId)).hasSize(6);
    }

    /**
     * Rien n'est marqué comme spécial : ni favori, ni archivé, ni compteur d'usage gonflé. Un jeu
     * de départ est un point de départ, pas une démonstration — et tout doit pouvoir disparaître
     * sans laisser de trace chez le coach qui préfère sa propre bibliothèque.
     */
    @Test
    void nothingIsMarkedAsSpecial() {
        UUID clubId = freshClub();
        starterKit.install(clubId);

        assertThat(templates(clubId)).allSatisfy(t -> {
            assertThat(t.isFavorite()).isFalse();
            assertThat(t.isArchived()).isFalse();
            assertThat(t.getUseCount()).isZero();
        });
    }

    /**
     * <b>Le cas qui compte : une vraie inscription, par l'API.</b>
     *
     * <p>Les cas ci-dessus appellent {@code install} directement. En production, l'appel part d'un
     * {@code afterCommit} de l'inscription — et c'est là que le seul défaut réel de ce chantier
     * s'est produit : avec une propagation {@code REQUIRED}, l'installation rejoignait une
     * transaction déjà committée et perdait tout, en silence. Le coach arrivait dans une
     * bibliothèque vide et aucun journal ne le disait. Il a fallu inscrire un vrai coach pour le
     * voir ; ce cas est là pour qu'il ne faille plus le refaire.</p>
     */
    @Test
    void aCoachWhoSignsUpFindsHisLibraryFilled() throws Exception {
        MockMvc mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        String email = "starter-" + UUID.randomUUID() + "@essai.fr";
        createdEmails.add(email);

        String body = mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"password123\","
                                + "\"fullName\":\"Coach Neuf\",\"clubName\":\"Club Neuf "
                                + UUID.randomUUID() + "\",\"termsAccepted\":true}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        UUID clubId = UUID.fromString(
                objectMapper.readTree(body).get("user").get("clubId").asText());
        createdClubs.add(clubId);

        assertThat(categoryRepository.findByClubIdAndDomainOrderBySortOrderAscNameAsc(
                clubId, CategoryDomain.COURSE)).hasSize(6);
        assertThat(drillRepository.findByClubIdOrderByCategoryAscNameAsc(clubId)).hasSize(6);
        assertThat(templates(clubId)).hasSize(11);
        // Les zones sont posées par le jeu de départ lui-même : sans elles, les séances n'auraient
        // aucune prescription à porter.
        assertThat(zoneRepository.existsByClubId(clubId)).isTrue();
    }
}
