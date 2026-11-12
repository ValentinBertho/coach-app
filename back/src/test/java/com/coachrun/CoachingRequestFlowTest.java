package com.coachrun;

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

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Le parcours complet du hub : un athlète s'inscrit seul, trouve un coach, demande, et travaille.
 *
 * <p>C'est le test qui dit si la boucle est fermée. Tout le reste — vitrine, annuaire, photo — n'a
 * de sens que si ce chemin-là aboutit : un compte sans coach, une demande, une acceptation, et un
 * athlète qui a soudain un calendrier.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CoachingRequestFlowTest {

    @Autowired private WebApplicationContext context;
    @Autowired private DemoSeedService demoSeedService;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private com.coachrun.repository.UserRepository userRepository;

    private MockMvc mvc;
    private String coachBearer;
    private String adminBearer;
    private String slug;

    @BeforeEach
    void setUp() throws Exception {
        demoSeedService.seed();
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        coachBearer = bearer(DemoSeedService.HEAD_COACH_EMAIL);
        adminBearer = bearer(DemoSeedService.ADMIN_EMAIL);
        slug = publishCoachProfile();
    }

    /**
     * Le parcours nominal, de bout en bout.
     *
     * <p>Avant l'acceptation, l'athlète n'a <b>pas de fiche</b> : c'est l'inversion que tout le hub
     * organise, et le compte doit exister sans elle. Après, il en a une, dans l'espace du coach.</p>
     */
    @Test
    void anAthleteSignsUpAloneThenGetsACoach() throws Exception {
        String athlete = registerAthlete("nina@exemple.fr");

        // Un athlète tout neuf n'appartient à personne : ni club, ni fiche.
        JsonNode me = json(mvc.perform(get("/me").header("Authorization", athlete)));
        assertThat(me.get("athleteId").isNull()).as("aucune fiche avant d'avoir un coach").isTrue();
        assertThat(me.get("clubId").isNull()).as("aucun club non plus").isTrue();

        String requestId = submitRequest(athlete);

        JsonNode inbox = json(mvc.perform(get("/me/received-requests")
                .header("Authorization", coachBearer)).andExpect(status().isOk()));
        assertThat(inbox).hasSize(1);
        assertThat(inbox.get(0).get("athleteGoal").asText()).contains("premier marathon");

        mvc.perform(post("/me/received-requests/{id}/accept", requestId)
                .header("Authorization", coachBearer)).andExpect(status().isOk());

        // Après acceptation, l'athlète a une fiche et un espace.
        JsonNode after = json(mvc.perform(get("/me").header("Authorization", athlete)));
        assertThat(after.get("athleteId").isNull()).as("la fiche existe désormais").isFalse();
        assertThat(after.get("clubId").isNull()).as("il a rejoint l'espace du coach").isFalse();
    }

    /**
     * L'athlète venu du hub est <b>privé</b> : il a choisi un coach, pas une organisation. Un autre
     * coach du même espace ne doit pas le voir.
     */
    @Test
    void anAthleteFromTheHubIsPrivateToTheCoachWhoAccepted() throws Exception {
        String athlete = registerAthlete("nina@exemple.fr");
        String requestId = submitRequest(athlete);
        mvc.perform(post("/me/received-requests/{id}/accept", requestId)
                .header("Authorization", coachBearer)).andExpect(status().isOk());

        String athleteId = json(mvc.perform(get("/me").header("Authorization", athlete)))
                .get("athleteId").asText();
        String clubId = json(mvc.perform(get("/me").header("Authorization", athlete)))
                .get("clubId").asText();

        String assistant = bearer(DemoSeedService.COACH_EMAIL);
        mvc.perform(get("/clubs/{c}/athletes/{a}", clubId, athleteId)
                        .header("Authorization", assistant))
                .andExpect(status().isForbidden());

        mvc.perform(get("/clubs/{c}/athletes/{a}", clubId, athleteId)
                        .header("Authorization", coachBearer))
                .andExpect(status().isOk());
    }

    /** Une adresse non vérifiée ne peut pas solliciter : le coach doit pouvoir répondre. */
    @Test
    void anUnverifiedAthleteCannotSolicitACoach() throws Exception {
        String athlete = registerAthleteWithoutVerifying("pas.verifie@exemple.fr");

        mvc.perform(post("/me/coaching-requests").header("Authorization", athlete)
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding(StandardCharsets.UTF_8)
                        .content(requestBody()))
                .andExpect(status().isForbidden());
    }

    /** Une seule demande en attente par couple : une file noyée est une file qu'on n'ouvre plus. */
    @Test
    void anAthleteCannotFloodTheSameCoach() throws Exception {
        String athlete = registerAthlete("nina@exemple.fr");
        submitRequest(athlete);

        mvc.perform(post("/me/coaching-requests").header("Authorization", athlete)
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding(StandardCharsets.UTF_8)
                        .content(requestBody()))
                .andExpect(status().isConflict());
    }

    /** Le coach pose UNE question, l'athlète répond UNE fois : il n'y a pas de messagerie avant. */
    @Test
    void theExchangeBeforeAcceptanceIsOneQuestionAndOneAnswer() throws Exception {
        String athlete = registerAthlete("nina@exemple.fr");
        String requestId = submitRequest(athlete);

        mvc.perform(post("/me/received-requests/{id}/ask", requestId)
                        .header("Authorization", coachBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding(StandardCharsets.UTF_8)
                        .content("{\"note\":\"Combien de séances par semaine ?\"}"))
                .andExpect(status().isOk());

        // Une seconde question est refusée : la suite se passe dans la messagerie, après l'accord.
        mvc.perform(post("/me/received-requests/{id}/ask", requestId)
                        .header("Authorization", coachBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding(StandardCharsets.UTF_8)
                        .content("{\"note\":\"Et vos disponibilités ?\"}"))
                .andExpect(status().isConflict());

        mvc.perform(post("/me/coaching-requests/{id}/answer", requestId)
                        .header("Authorization", athlete)
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding(StandardCharsets.UTF_8)
                        .content("{\"note\":\"Quatre.\"}"))
                .andExpect(status().isOk());

        mvc.perform(post("/me/coaching-requests/{id}/answer", requestId)
                        .header("Authorization", athlete)
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding(StandardCharsets.UTF_8)
                        .content("{\"note\":\"En fait cinq.\"}"))
                .andExpect(status().isConflict());
    }

    /** Un refus porte son motif : sans lui, l'athlète redemande sans savoir ce qui n'allait pas. */
    @Test
    void aDeclineCarriesItsReasonToTheAthlete() throws Exception {
        String athlete = registerAthlete("nina@exemple.fr");
        String requestId = submitRequest(athlete);

        mvc.perform(post("/me/received-requests/{id}/decline", requestId)
                        .header("Authorization", coachBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding(StandardCharsets.UTF_8)
                        .content("{\"note\":\"Je suis complet jusqu'en septembre.\"}"))
                .andExpect(status().isOk());

        JsonNode mine = json(mvc.perform(get("/me/coaching-requests").header("Authorization", athlete)));
        assertThat(mine.get(0).get("status").asText()).isEqualTo("DECLINED");
        assertThat(mine.get(0).get("declineReason").asText()).contains("complet");
    }

    /** Retirée n'est pas refusée : l'athlète doit pouvoir se raviser sans se croire éconduit. */
    @Test
    void withdrawingIsNotBeingDeclined() throws Exception {
        String athlete = registerAthlete("nina@exemple.fr");
        String requestId = submitRequest(athlete);

        mvc.perform(delete("/me/coaching-requests/{id}", requestId).header("Authorization", athlete))
                .andExpect(status().isOk());

        JsonNode mine = json(mvc.perform(get("/me/coaching-requests").header("Authorization", athlete)));
        assertThat(mine.get(0).get("status").asText()).isEqualTo("WITHDRAWN");
    }

    /**
     * Le coach ne voit pas les coordonnées de l'athlète tant qu'il n'a pas accepté. Si une demande
     * livrait l'adresse, il suffirait d'en recevoir pour se faire un fichier, et refuser
     * n'aurait plus d'effet.
     */
    @Test
    void theCoachSeesNoContactDetailsBeforeAccepting() throws Exception {
        String athlete = registerAthlete("nina@exemple.fr");
        submitRequest(athlete);

        String inbox = body(mvc.perform(get("/me/received-requests")
                .header("Authorization", coachBearer)).andExpect(status().isOk()));

        assertThat(inbox).doesNotContain("nina@exemple.fr");
        assertThat(inbox).doesNotContain("birthDate");
    }

    /** L'inscription libre est fermée aux moins de 16 ans, et le refus dit le chemin qui reste. */
    @Test
    void selfRegistrationIsClosedUnderSixteen() throws Exception {
        String body = mvc.perform(post("/public/athlete-registration")
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding(StandardCharsets.UTF_8)
                        .content(registrationBody("jeune@exemple.fr", LocalDate.now().minusYears(14))))
                .andExpect(status().isBadRequest())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertThat(body).contains("16 ans");
        assertThat(body).as("le refus nomme le chemin qui reste ouvert").contains("inviter");
    }

    /**
     * La sortie, côté athlète. Rien n'est détruit : il redevient un compte sans coach, libre de
     * repartir dans l'annuaire, et sa fiche reste chez celui qui l'a tenue.
     */
    @Test
    void anAthleteCanLeaveTheirCoachAndGoBackToTheDirectory() throws Exception {
        String email = "nina@exemple.fr";
        String athlete = registerAthlete(email);
        acceptRequestFrom(athlete);

        mvc.perform(post("/me/coach/end").header("Authorization", athlete)
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding(StandardCharsets.UTF_8)
                        .content("{\"note\":\"Je change de discipline.\"}"))
                .andExpect(status().isNoContent());

        // Reconnexion : le détachement FERME LES SESSIONS du compte, parce que le jeton affirme un
        // `athleteId` et un `clubId` qu'il ne relit jamais en base. C'est ce que fait
        // l'application, et c'est ce qui empêche un ancien athlète de continuer à se présenter
        // comme membre du club de son ancien coach.
        //
        // Sans elle, ce test dépendait de l'HORLOGE : le seuil de révocation est tronqué à la
        // seconde et un jeton émis dans la même seconde est tenu pour frais. Il passait donc quand
        // l'inscription et la sortie tombaient dans la même seconde, et échouait dès que la suite
        // s'allongeait — un échec qui accusait le code d'un défaut appartenant au test.
        athlete = bearerFor(email);

        JsonNode me = json(mvc.perform(get("/me").header("Authorization", athlete)));
        assertThat(me.get("athleteId").isNull()).as("plus de fiche courante").isTrue();
        assertThat(me.get("clubId").isNull()).as("il a quitté l'espace du coach").isTrue();
    }

    /** La même sortie, à l'initiative du coach. Les deux parties peuvent partir. */
    @Test
    void aCoachCanEndTheCoachingToo() throws Exception {
        String email = "nina@exemple.fr";
        String athlete = registerAthlete(email);
        acceptRequestFrom(athlete);
        JsonNode me = json(mvc.perform(get("/me").header("Authorization", athlete)));
        String clubId = me.get("clubId").asText();
        String athleteId = me.get("athleteId").asText();

        mvc.perform(post("/clubs/{c}/athletes/{a}/end-relation", clubId, athleteId)
                        .header("Authorization", coachBearer))
                .andExpect(status().isNoContent());

        // Reconnexion, pour la raison dite au cas précédent : la sortie ferme les sessions.
        assertThat(json(mvc.perform(get("/me").header("Authorization", bearerFor(email))))
                .get("athleteId").isNull()).isTrue();
    }

    /**
     * Ce que la fin d'une relation laisse à l'ancien coach : la LECTURE, jamais l'écriture.
     *
     * <p>Sans cette nuance, clore une relation rendait la fiche illisible par tout le monde —
     * l'athlète reparti, le coach forclos — soit un historique d'entraînement conservé pour
     * personne. Or c'est lui qui l'a écrit. Prescrire, en revanche, n'a plus de sens : on ne pose
     * pas de séance à quelqu'un qu'on ne suit plus.</p>
     */
    @Test
    void theFormerCoachKeepsReadAccessButLosesWrite() throws Exception {
        String athlete = registerAthlete("nina@exemple.fr");
        acceptRequestFrom(athlete);
        String clubId = json(mvc.perform(get("/me").header("Authorization", athlete)))
                .get("clubId").asText();
        String athleteId = json(mvc.perform(get("/me").header("Authorization", athlete)))
                .get("athleteId").asText();

        mvc.perform(post("/me/coach/end").header("Authorization", athlete))
                .andExpect(status().isNoContent());

        mvc.perform(get("/clubs/{c}/athletes/{a}", clubId, athleteId)
                        .header("Authorization", coachBearer))
                .andExpect(status().isOk());

        // L'invitation est une action d'écriture : elle doit être refusée.
        mvc.perform(post("/clubs/{c}/athletes/{a}/invitation", clubId, athleteId)
                        .header("Authorization", coachBearer))
                .andExpect(status().isForbidden());
    }

    /** Un coach qui n'a jamais suivi cet athlète ne récupère rien à la fin de la relation. */
    @Test
    void anotherCoachGainsNothingWhenARelationEnds() throws Exception {
        String athlete = registerAthlete("nina@exemple.fr");
        acceptRequestFrom(athlete);
        String clubId = json(mvc.perform(get("/me").header("Authorization", athlete)))
                .get("clubId").asText();
        String athleteId = json(mvc.perform(get("/me").header("Authorization", athlete)))
                .get("athleteId").asText();

        mvc.perform(post("/me/coach/end").header("Authorization", athlete))
                .andExpect(status().isNoContent());

        mvc.perform(get("/clubs/{c}/athletes/{a}", clubId, athleteId)
                        .header("Authorization", bearer(DemoSeedService.COACH_EMAIL)))
                .andExpect(status().isForbidden());
    }

    /**
     * Autorisation croisée (même famille que {@code CrossAthleteAuthorizationTest}) : une demande
     * adressée à un coach ne se traite que par lui. Un confrère du même espace qui devine son
     * identifiant ne peut ni l'accepter, ni la refuser, ni y poser sa question — et la réponse
     * est celle d'une demande inexistante, pour ne rien révéler.
     */
    @Test
    void anotherCoachCannotActOnARequestAddressedToSomeoneElse() throws Exception {
        String athlete = registerAthlete("nina@exemple.fr");
        String requestId = submitRequest(athlete);
        String colleague = bearer(DemoSeedService.COACH_EMAIL);

        mvc.perform(post("/me/received-requests/{id}/accept", requestId)
                .header("Authorization", colleague)).andExpect(status().isNotFound());
        mvc.perform(post("/me/received-requests/{id}/decline", requestId)
                .header("Authorization", colleague)
                .contentType(MediaType.APPLICATION_JSON).content("{\"note\":\"non\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(post("/me/received-requests/{id}/ask", requestId)
                .header("Authorization", colleague)
                .contentType(MediaType.APPLICATION_JSON).content("{\"note\":\"Disponible le mardi ?\"}"))
                .andExpect(status().isNotFound());

        JsonNode mine = json(mvc.perform(get("/me/coaching-requests").header("Authorization", athlete)));
        assertThat(mine.get(0).get("status").asText()).isEqualTo("PENDING");
        assertThat(mine.get(0).get("coachQuestion").isNull()).isTrue();
    }

    /** Seul le coach référent met fin au suivi : un confrère, même autorisé en écriture, non. */
    @Test
    void onlyTheReferentCoachCanEndTheCoaching() throws Exception {
        String athlete = registerAthlete("nina@exemple.fr");
        acceptRequestFrom(athlete);
        JsonNode me = json(mvc.perform(get("/me").header("Authorization", athlete)));
        String clubId = me.get("clubId").asText();
        String athleteId = me.get("athleteId").asText();

        mvc.perform(post("/clubs/{c}/athletes/{a}/end-relation", clubId, athleteId)
                        .header("Authorization", bearer(DemoSeedService.COACH_EMAIL)))
                .andExpect(status().is4xxClientError());

        assertThat(json(mvc.perform(get("/me").header("Authorization", athlete)))
                .get("athleteId").isNull()).as("le suivi continue").isFalse();
    }

    /** Parti, l'athlète peut solliciter à nouveau : sortir ne doit pas fermer la porte. */
    @Test
    void leavingLetsTheAthleteAskAgain() throws Exception {
        String athlete = registerAthlete("nina@exemple.fr");
        acceptRequestFrom(athlete);
        mvc.perform(post("/me/coach/end").header("Authorization", athlete))
                .andExpect(status().isNoContent());

        // La sortie ferme les sessions : on se reconnecte. Réutiliser l'ancien jeton ne passait
        // que lorsqu'il avait été émis dans la même seconde que la fermeture.
        athlete = bearerFor("nina@exemple.fr");
        mvc.perform(post("/me/coaching-requests").header("Authorization", athlete)
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding(StandardCharsets.UTF_8)
                        .content(requestBody()))
                .andExpect(status().isCreated());
    }

    /**
     * Une fiche que la plateforme ne publie pas ne se distingue pas d'une fiche inexistante.
     *
     * <p>Le défaut fermé ici : la recherche par slug ne filtrait pas la visibilité. Une fiche
     * <b>suspendue</b> — c'est-à-dire une sanction — répondait 409 en nommant son coach, là où un
     * slug inventé répondait 404. Un athlète qui devinait des slugs apprenait donc l'existence et
     * l'état de fiches que la plateforme a retirées de l'annuaire.</p>
     */
    @Test
    void aSuspendedProfileIsIndistinguishableFromAnUnknownOne() throws Exception {
        String athlete = registerAthlete("suspendu-" + java.util.UUID.randomUUID() + "@exemple.fr");

        String unknown = message(mvc.perform(post("/me/coaching-requests")
                        .header("Authorization", athlete)
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding(StandardCharsets.UTF_8)
                        .content(requestBody("personne-de-ce-nom")))
                .andExpect(status().isNotFound()));

        // La fiche du coach de démonstration est publiée par le setUp : on la suspend.
        JsonNode published = json(mvc.perform(get("/admin/coach-profiles").param("status", "PUBLISHED")
                .header("Authorization", adminBearer)));
        mvc.perform(post("/admin/coach-profiles/{id}/suspend",
                        published.get("content").get(0).get("id").asText())
                        .header("Authorization", adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding(StandardCharsets.UTF_8)
                        .content("{\"note\":\"Vérification en cours.\"}"))
                .andExpect(status().isOk());

        String suspended = message(mvc.perform(post("/me/coaching-requests")
                        .header("Authorization", athlete)
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding(StandardCharsets.UTF_8)
                        .content(requestBody(slug)))
                .andExpect(status().isNotFound()));

        assertThat(suspended)
                .as("même statut ET même message : sinon le couple 404/409 reste un oracle")
                .isEqualTo(unknown);
        assertThat(suspended)
                .as("le nom du coach ne doit pas filtrer")
                .doesNotContain("Coach Démo");

        // Remise en état. Ce cas suspend la fiche du coach de DÉMONSTRATION, que toute la classe
        // partage, et les écritures faites par requête HTTP survivent d'une méthode à l'autre :
        // laisser la fiche suspendue faisait échouer les cas de fin de coaching, qui n'ont rien à
        // voir. Un test qui touche à l'état commun le rend comme il l'a trouvé.
        mvc.perform(post("/admin/coach-profiles/{id}/reinstate",
                        published.get("content").get(0).get("id").asText())
                        .header("Authorization", adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding(StandardCharsets.UTF_8)
                        .content("{\"note\":\"Vérification close.\"}"))
                .andExpect(status().isOk());
    }

    /**
     * Le pendant : une fiche <b>fermée</b> reste consultable, et le dire est utile.
     *
     * <p>Elle figure dans l'annuaire — son nom est déjà public — et « il ne prend personne en ce
     * moment » évite à l'athlète de croire à une panne. Confondre ce cas avec une fiche retirée
     * aurait rendu le correctif plus discret que juste.</p>
     */
    @Test
    void aClosedProfileStillSaysThatItTakesNobody() throws Exception {
        String athlete = registerAthlete("ferme-" + java.util.UUID.randomUUID() + "@exemple.fr");

        mvc.perform(post("/me/coach-profile/accepting").param("accepting", "false")
                .header("Authorization", coachBearer)).andExpect(status().isOk());
        // Même précaution que ci-dessus : la fiche est rouverte avant de sortir.

        String closed = message(mvc.perform(post("/me/coaching-requests")
                        .header("Authorization", athlete)
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding(StandardCharsets.UTF_8)
                        .content(requestBody(slug)))
                .andExpect(status().isConflict()));

        assertThat(closed).contains("ne prend pas de nouveaux athlètes");

        mvc.perform(post("/me/coach-profile/accepting").param("accepting", "true")
                .header("Authorization", coachBearer)).andExpect(status().isOk());
    }

    /**
     * Le visiteur écrit sa demande <b>en s'inscrivant</b> ; elle part d'elle-même quand il confirme
     * son e-mail.
     *
     * <p>Avant, il s'inscrivait, confirmait, puis devait retrouver le coach et refaire sa demande.
     * La garde ne bouge pas pour autant : tant que l'adresse n'est pas confirmée, le coach ne
     * reçoit rien.</p>
     */
    @Test
    void aRequestWrittenAtSignupLeavesWhenTheEmailIsVerified() throws Exception {
        String email = "inscription-" + java.util.UUID.randomUUID() + "@exemple.fr";
        String athlete = registerWithRequest(email, slug,
                "Je prépare mon premier marathon pour avril et je cherche un suivi hebdomadaire.")
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        athlete = "Bearer " + objectMapper.readTree(athlete).get("accessToken").asText();

        // Avant la confirmation : la demande attend, visible de l'athlète, invisible du coach.
        JsonNode account = json(mvc.perform(get("/me/account").header("Authorization", athlete))
                .andExpect(status().isOk()));
        assertThat(account.get("pendingRequest").get("coachSlug").asText()).isEqualTo(slug);
        assertThat(account.get("pendingRequest").get("coachName").asText()).isNotBlank();
        assertThat(json(mvc.perform(get("/me/coaching-requests").header("Authorization", athlete))
                .andExpect(status().isOk())).size()).isZero();
        assertThat(body(mvc.perform(get("/me/received-requests").header("Authorization", coachBearer))
                .andExpect(status().isOk())))
                .as("rien n'arrive au coach d'une adresse non confirmée")
                .doesNotContain("premier marathon pour avril");

        String token = userRepository.findByEmailIgnoreCase(email).orElseThrow().getVerifyToken();
        JsonNode verified = json(mvc.perform(post("/public/verify-email/{token}", token))
                .andExpect(status().isOk()));
        assertThat(verified.get("verified").asBoolean()).isTrue();
        assertThat(verified.get("requestSentTo").asText())
                .as("l'écran doit pouvoir dire à qui la demande est partie").isNotBlank();

        JsonNode mine = json(mvc.perform(get("/me/coaching-requests").header("Authorization", athlete))
                .andExpect(status().isOk()));
        assertThat(mine.size()).isEqualTo(1);
        assertThat(mine.get(0).get("status").asText()).isEqualTo("PENDING");
        assertThat(mine.get(0).get("message").asText()).contains("premier marathon pour avril");
        assertThat(body(mvc.perform(get("/me/received-requests").header("Authorization", coachBearer))
                .andExpect(status().isOk()))).contains("premier marathon pour avril");
        assertThat(json(mvc.perform(get("/me/account").header("Authorization", athlete)))
                .get("pendingRequest").isNull())
                .as("partie, la demande n'attend plus").isTrue();
    }

    /**
     * Une demande jointe à l'inscription est vérifiée avant de créer le compte : le visiteur est
     * encore devant son formulaire, c'est le seul moment où le lui dire sert à quelque chose.
     */
    @Test
    void aSignupRequestIsCheckedBeforeTheAccountIsCreated() throws Exception {
        String tooShort = "court-" + java.util.UUID.randomUUID() + "@exemple.fr";
        registerWithRequest(tooShort, slug, "Bonjour").andExpect(status().isBadRequest());
        assertThat(userRepository.findByEmailIgnoreCase(tooShort)).isEmpty();

        String unknown = "inconnu-" + java.util.UUID.randomUUID() + "@exemple.fr";
        registerWithRequest(unknown, "personne-de-ce-nom",
                "Je prépare mon premier marathon pour avril.").andExpect(status().isNotFound());
        assertThat(userRepository.findByEmailIgnoreCase(unknown)).isEmpty();
    }

    /**
     * Le coach ferme sa fiche entre l'inscription et la confirmation : la vérification de l'e-mail
     * réussit quand même, aucune demande ne part, et rien ne reste en attente.
     */
    @Test
    void aSignupRequestThatCanNoLongerLeaveDoesNotBlockTheVerification() throws Exception {
        String email = "tard-" + java.util.UUID.randomUUID() + "@exemple.fr";
        String athlete = "Bearer " + objectMapper.readTree(registerWithRequest(email, slug,
                "Je prépare mon premier marathon pour avril.")
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString())
                .get("accessToken").asText();
        mvc.perform(post("/me/coach-profile/accepting").param("accepting", "false")
                .header("Authorization", coachBearer)).andExpect(status().isOk());

        String token = userRepository.findByEmailIgnoreCase(email).orElseThrow().getVerifyToken();
        JsonNode verified = json(mvc.perform(post("/public/verify-email/{token}", token))
                .andExpect(status().isOk()));
        assertThat(verified.get("verified").asBoolean()).isTrue();
        assertThat(verified.has("requestSentTo")).isFalse();
        assertThat(userRepository.findByEmailIgnoreCase(email).orElseThrow().isEmailVerified()).isTrue();
        JsonNode account = json(mvc.perform(get("/me/account").header("Authorization", athlete)));
        assertThat(account.get("pendingRequest").isNull()).isTrue();
        assertThat(account.get("goal").asText())
                .as("le texte n'est pas perdu : il reste l'objectif du compte")
                .contains("premier marathon");

        mvc.perform(post("/me/coach-profile/accepting").param("accepting", "true")
                .header("Authorization", coachBearer)).andExpect(status().isOk());
    }

    // ------------------------------------------------------------------ utilitaires

    private org.springframework.test.web.servlet.ResultActions registerWithRequest(
            String email, String coachSlug, String message) throws Exception {
        return mvc.perform(post("/public/athlete-registration")
                .contentType(MediaType.APPLICATION_JSON)
                .characterEncoding(StandardCharsets.UTF_8)
                .content("{\"email\":\"" + email + "\",\"password\":\"password1234\","
                        + "\"firstName\":\"Nina\",\"lastName\":\"Roy\","
                        + "\"birthDate\":\"" + LocalDate.now().minusYears(28) + "\","
                        + "\"coachSlug\":\"" + coachSlug + "\","
                        + "\"requestMessage\":\"" + message + "\","
                        + "\"termsAccepted\":true,\"healthDataConsent\":true}"));
    }

    /** Envoie une demande et la fait accepter : le point de départ des tests de sortie. */
    private void acceptRequestFrom(String athleteBearer) throws Exception {
        String requestId = submitRequest(athleteBearer);
        mvc.perform(post("/me/received-requests/{id}/accept", requestId)
                .header("Authorization", coachBearer)).andExpect(status().isOk());
    }


    private String submitRequest(String athleteBearer) throws Exception {
        return json(mvc.perform(post("/me/coaching-requests").header("Authorization", athleteBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding(StandardCharsets.UTF_8)
                        .content(requestBody()))
                .andExpect(status().isCreated())).get("id").asText();
    }

    /**
     * Le message d'erreur seul.
     *
     * <p>Et non le corps entier : `ApiError` porte un `timestamp` qui change à chaque appel, si
     * bien qu'une comparaison brute échouerait toujours — et masquerait ce qu'on veut vraiment
     * comparer, le texte rendu au client.</p>
     */
    private String message(org.springframework.test.web.servlet.ResultActions actions) throws Exception {
        return json(actions).get("message").asText();
    }

    /** Un jeton frais pour un athlète inscrit par {@link #registerAthlete(String)}. */
    private String bearerFor(String email) throws Exception {
        JsonNode res = json(mvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding(StandardCharsets.UTF_8)
                        .content("{\"email\":\"" + email + "\",\"password\":\"password1234\"}"))
                .andExpect(status().isOk()));
        return "Bearer " + res.get("accessToken").asText();
    }

    private String requestBody() {
        return requestBody(slug);
    }

    /** Même corps, vers un slug choisi : sert à comparer une fiche connue et une fiche inventée. */
    private String requestBody(String coachSlug) {
        return "{\"coachSlug\":\"" + coachSlug + "\",\"message\":\"Je prépare mon premier marathon "
                + "au printemps et je cherche un accompagnement structuré.\"}";
    }

    /** Inscrit un athlète et vérifie son adresse — l'état normal avant de solliciter. */
    private String registerAthlete(String email) throws Exception {
        String bearer = registerAthleteWithoutVerifying(email);
        var user = userRepository.findByEmailIgnoreCase(email).orElseThrow();
        user.setEmailVerified(true);
        userRepository.saveAndFlush(user);
        return bearer;
    }

    private String registerAthleteWithoutVerifying(String email) throws Exception {
        JsonNode res = json(mvc.perform(post("/public/athlete-registration")
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding(StandardCharsets.UTF_8)
                        .content(registrationBody(email, LocalDate.now().minusYears(28))))
                .andExpect(status().isCreated()));
        return "Bearer " + res.get("accessToken").asText();
    }

    private String registrationBody(String email, LocalDate birthDate) {
        return "{\"email\":\"" + email + "\",\"password\":\"password1234\","
                + "\"firstName\":\"Nina\",\"lastName\":\"Roy\","
                + "\"birthDate\":\"" + birthDate + "\","
                + "\"goal\":\"Finir mon premier marathon au printemps\","
                + "\"termsAccepted\":true,\"healthDataConsent\":true}";
    }

    /** Publie la fiche du coach de démonstration : sans elle, personne n'est sollicitable. */
    private String publishCoachProfile() throws Exception {
        mvc.perform(put("/me/coach-profile").header("Authorization", coachBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding(StandardCharsets.UTF_8)
                        .content("{\"headline\":\"Coach route\",\"bio\":\"" + "x".repeat(130) + "\","
                                + "\"disciplines\":[\"ROUTE\"],\"specialties\":[\"MARATHON\"],"
                                + "\"levels\":[],\"languages\":[\"fr\"],\"remote\":true,"
                                + "\"inPerson\":false}"))
                .andExpect(status().isOk());
        mvc.perform(post("/me/coach-profile/offers").header("Authorization", coachBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding(StandardCharsets.UTF_8)
                        .content("{\"name\":\"Suivi mensuel\",\"amountCents\":9000,"
                                + "\"periodicity\":\"MONTHLY\",\"active\":true,\"position\":0}"))
                .andExpect(status().isCreated());
        mvc.perform(post("/me/coach-profile/submit").header("Authorization", coachBearer))
                .andExpect(status().isOk());
        JsonNode queue = json(mvc.perform(get("/admin/coach-profiles").param("status", "PENDING")
                .header("Authorization", adminBearer)));
        mvc.perform(post("/admin/coach-profiles/{id}/approve", queue.get("content").get(0).get("id").asText())
                .header("Authorization", adminBearer)).andExpect(status().isOk());
        return json(mvc.perform(get("/me/coach-profile").header("Authorization", coachBearer)))
                .get("slug").asText();
    }

    private JsonNode json(org.springframework.test.web.servlet.ResultActions actions) throws Exception {
        return objectMapper.readTree(body(actions));
    }

    private String body(org.springframework.test.web.servlet.ResultActions actions) throws Exception {
        return actions.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private String bearer(String email) throws Exception {
        JsonNode res = objectMapper.readTree(mvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\""
                                + DemoSeedService.DEMO_PASSWORD + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString(StandardCharsets.UTF_8));
        return "Bearer " + MfaTestSupport.complete(mvc, context, email, res).get("accessToken").asText();
    }
}
