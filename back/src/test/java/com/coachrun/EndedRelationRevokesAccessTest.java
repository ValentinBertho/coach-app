package com.coachrun;

import com.coachrun.config.MultiCoachBackfill;
import com.coachrun.entity.CoachAthleteRelation;
import com.coachrun.repository.CoachAthleteRelationRepository;
import com.coachrun.service.DemoSeedService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Clore une relation coach ↔ athlète retire vraiment l'accès.
 *
 * <p><b>Ce que ces tests protègent.</b> {@code CoachAthleteRelation.active} existait depuis
 * l'origine sans que rien ne le mette jamais à {@code false}. Le faire ne retirait rien : faute de
 * relation référente active, {@code AthleteAccessValidator} retombait sur l'accès club, lequel rend
 * l'écriture au propriétaire du club — c'est-à-dire, pour un coach indépendant, au coach dont on
 * venait précisément de clore la relation. La fin d'une relation aurait été décorative.</p>
 *
 * <p>Le repli club reste indispensable pour les athlètes antérieurs au modèle multi-coach, qui
 * n'ont aucune relation référente : {@link #athleteWithoutAnyReferentKeepsLegacyClubAccess}
 * verrouille ce second cas, pour qu'on ne corrige pas le premier en cassant le second.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class EndedRelationRevokesAccessTest {

    @Autowired private WebApplicationContext context;
    @Autowired private DemoSeedService demoSeedService;
    @Autowired private CoachAthleteRelationRepository relationRepository;
    @Autowired private MultiCoachBackfill multiCoachBackfill;
    @Autowired private ObjectMapper objectMapper;

    private MockMvc mvc;
    private String ownerBearer;      // HEAD_COACH : propriétaire du club ET référent de l'athlète
    private String assistantBearer;  // COACH_ASSISTANT : permission READ explicite sur l'athlète
    private String clubId;
    private UUID demoAthleteId;

    @BeforeEach
    void setUp() throws Exception {
        demoSeedService.seed();
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();

        JsonNode owner = login(DemoSeedService.HEAD_COACH_EMAIL);
        ownerBearer = "Bearer " + owner.get("accessToken").asText();
        clubId = owner.get("user").get("clubId").asText();
        assistantBearer = "Bearer " + login(DemoSeedService.COACH_EMAIL).get("accessToken").asText();
        demoAthleteId = UUID.fromString(
                login(DemoSeedService.ATHLETE_EMAIL).get("user").get("athleteId").asText());
    }

    /**
     * Le cas nominal du coach indépendant : il est à la fois propriétaire du club qui porte la
     * fiche et coach référent de l'athlète. Clore la relation doit lui retirer <b>l'écriture</b> —
     * sans le correctif, l'accès club la lui rendait intégralement.
     *
     * <p><b>Il garde la lecture</b>, et c'est une décision prise en écrivant la fin de relation,
     * pas un reste du défaut. Retirer les deux rendait la fiche illisible par tout le monde :
     * l'athlète détaché est reparti vers l'annuaire, son ancien coach était forclos, et
     * l'historique que la décision 9 attache au coach était conservé <em>pour personne</em>. Il
     * l'a écrit, il peut le relire ; il ne prescrit plus à quelqu'un qu'il ne suit plus.</p>
     */
    @Test
    void endedReferentRelationRevokesWritingButNotReadingForTheClubOwner() throws Exception {
        // Avant : le référent lit son athlète.
        mvc.perform(get("/clubs/{c}/athletes/{a}", clubId, demoAthleteId)
                        .header("Authorization", ownerBearer))
                .andExpect(status().isOk());

        CoachAthleteRelation referent = endReferentRelation();

        // La clôture est horodatée et attribuée : un booléen seul ne dirait pas depuis quand.
        assertThat(referent.isActive()).isFalse();
        assertThat(referent.getEndedAt()).isNotNull();
        assertThat(referent.getEndedByUserId()).isNotNull();

        // Après : il relit encore la fiche qu'il a tenue…
        mvc.perform(get("/clubs/{c}/athletes/{a}", clubId, demoAthleteId)
                        .header("Authorization", ownerBearer))
                .andExpect(status().isOk());

        // … mais il n'écrit plus. C'est ici que se joue le correctif : avant lui, l'accès club
        // rendait l'écriture au propriétaire dès la relation close.
        mvc.perform(post("/clubs/{c}/athletes/{a}/invitation", clubId, demoAthleteId)
                        .header("Authorization", ownerBearer))
                .andExpect(status().isForbidden());
    }

    /**
     * L'autre moitié de la règle, et celle qui la rend sûre : la lecture conservée est celle de
     * <b>l'ancien référent</b>, pas celle du club.
     *
     * <p>Sans cette borne, « l'ex-coach garde la lecture » deviendrait « tout coach du club lit un
     * athlète que plus personne ne suit » — exactement l'élévation d'accès que ce fichier
     * existe pour interdire.</p>
     */
    @Test
    void aCoachWhoNeverFollowedTheAthleteGetsNothingAfterTheEnd() throws Exception {
        endReferentRelation();

        mvc.perform(get("/clubs/{c}/athletes/{a}", clubId, demoAthleteId)
                        .header("Authorization", assistantBearer))
                .andExpect(status().isForbidden());
    }

    /**
     * Une permission explicite ne survit pas à la clôture de la relation référente qui l'a
     * accordée. Un athlète que plus personne ne suit n'est plus le sujet de personne : on échoue
     * fermé, et c'est au transfert de relation (et non à une permission orpheline) de rouvrir
     * l'accès à un successeur.
     */
    @Test
    void explicitPermissionDoesNotSurviveTheEndOfTheReferentRelation() throws Exception {
        // L'assistant lit l'athlète grâce à sa permission READ.
        mvc.perform(get("/clubs/{c}/athletes/{a}", clubId, demoAthleteId)
                        .header("Authorization", assistantBearer))
                .andExpect(status().isOk());

        endReferentRelation();

        mvc.perform(get("/clubs/{c}/athletes/{a}", clubId, demoAthleteId)
                        .header("Authorization", assistantBearer))
                .andExpect(status().isForbidden());
    }

    /**
     * Le repli historique reste en place : un athlète qui n'a <b>jamais</b> eu de relation
     * référente — donnée antérieure au modèle multi-coach — demeure accessible aux coachs de son
     * club. Sans lui, le correctif ci-dessus verrouillerait des athlètes non backfillés.
     */
    @Test
    void athleteWithoutAnyReferentKeepsLegacyClubAccess() throws Exception {
        List<CoachAthleteRelation> relations = relationRepository.findByAthleteIdAndActiveTrue(demoAthleteId);
        assertThat(relations).as("le jeu démo pose bien une relation référente").isNotEmpty();
        relationRepository.deleteAll(relations);
        relationRepository.flush();

        assertThat(relationRepository.existsByAthleteIdAndReferentTrue(demoAthleteId)).isFalse();

        mvc.perform(get("/clubs/{c}/athletes/{a}", clubId, demoAthleteId)
                        .header("Authorization", ownerBearer))
                .andExpect(status().isOk());
    }

    /**
     * Le backfill multi-coach s'exécute à <b>chaque</b> démarrage, et sa clé d'idempotence était
     * l'existence d'un référent <em>actif</em> : il retraitait donc l'athlète dont on venait de
     * clore la relation. Ici le coach détaché est le head coach du club — le cas nominal d'un
     * indépendant — et la réinsertion de la même paire (coach, athlète) violait
     * {@code uq_coach_athlete} : l'exception, levée dans un {@code ApplicationRunner}, empêchait
     * l'application de démarrer. Quand le coach détaché n'est pas le head coach, la relation était
     * recréée au profit de ce dernier, à qui l'on rendait un accès en silence.
     */
    @Test
    void startupBackfillDoesNotResurrectAnEndedRelation() throws Exception {
        endReferentRelation();

        multiCoachBackfill.run(null);
        relationRepository.flush();

        assertThat(relationRepository.findByAthleteIdAndReferentTrueAndActiveTrue(demoAthleteId))
                .as("le backfill ne recrée pas de référent pour une relation close")
                .isEmpty();

        // Le head coach relit la fiche qu'il a tenue — mais parce qu'il en était le référent, et
        // non parce que le backfill lui aurait rendu une relation active. C'est cette dernière que
        // l'assertion ci-dessus interdit, et c'est elle qui rendait l'écriture.
        mvc.perform(post("/clubs/{c}/athletes/{a}/invitation", clubId, demoAthleteId)
                        .header("Authorization", ownerBearer))
                .andExpect(status().isForbidden());
    }

    /**
     * Le pendant du test précédent : le backfill garde sa raison d'être. Un athlète antérieur au
     * modèle multi-coach, qui n'a jamais eu de référent, s'en voit bien attribuer un.
     */
    @Test
    void startupBackfillStillRepairsAnAthleteThatNeverHadAReferent() {
        relationRepository.deleteAll(relationRepository.findByAthleteIdAndActiveTrue(demoAthleteId));
        relationRepository.flush();

        multiCoachBackfill.run(null);
        relationRepository.flush();

        assertThat(relationRepository.findByAthleteIdAndReferentTrueAndActiveTrue(demoAthleteId))
                .as("un athlète sans aucun référent en reçoit un")
                .isPresent();
    }

    /** Clôt la relation référente de l'athlète démo et la renvoie, rechargée. */
    private CoachAthleteRelation endReferentRelation() {
        CoachAthleteRelation referent = relationRepository
                .findByAthleteIdAndReferentTrueAndActiveTrue(demoAthleteId)
                .orElseThrow(() -> new AssertionError("le jeu démo doit poser une relation référente"));
        referent.end(referent.getCoach().getId(), "fin de collaboration (test)");
        relationRepository.saveAndFlush(referent);
        return referent;
    }

    private JsonNode login(String email) throws Exception {
        return objectMapper.readTree(mvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\""
                                + DemoSeedService.DEMO_PASSWORD + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }
}
