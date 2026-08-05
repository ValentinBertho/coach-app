package com.coachrun;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Bout en bout des tours d'activité : import d'un TCX de fractionné, stockage JSON, relecture par
 * l'athlète depuis son portail.
 *
 * <p>Le chemin critique est celui du stockage : les tours passent par une colonne CLOB en JSON et
 * doivent revenir <em>identiques</em>. Une régression de sérialisation ne se verrait pas à
 * l'import — seulement le jour où un athlète ouvre sa séance et n'y trouve rien.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
class ActivityLapsEndpointTest {

    @Autowired private WebApplicationContext context;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private com.coachrun.repository.UserRepository userRepository;

    /** 2 × (400 m rapide / 200 m de récupération) : un fractionné minuscule mais complet. */
    private static final String TCX = """
            <?xml version="1.0" encoding="UTF-8"?>
            <TrainingCenterDatabase><Activities><Activity Sport="Running">
            %s</Activity></Activities></TrainingCenterDatabase>
            """.formatted(
                    lap("08:00:00", "08:01:24", 84, 400, 168, "45.7500", "45.7536")
                    + lap("08:01:24", "08:03:24", 120, 200, 132, "45.7536", "45.7554")
                    + lap("08:03:24", "08:04:46", 82, 400, 171, "45.7554", "45.7590")
                    + lap("08:04:46", "08:06:44", 118, 200, 130, "45.7590", "45.7608"));

    /** Un {@code <Lap>} TCX avec ses deux points de trace — dont les distances cumulées, piège
     *  classique : elles ne doivent jamais être prises pour la distance du tour. */
    private static String lap(String start, String end, int seconds, int meters, int hr,
                              String fromLat, String toLat) {
        return """
                  <Lap StartTime="2026-06-20T%sZ">
                    <TotalTimeSeconds>%d.0</TotalTimeSeconds>
                    <DistanceMeters>%d.0</DistanceMeters>
                    <AverageHeartRateBpm><Value>%d</Value></AverageHeartRateBpm>
                    <Track>
                      <Trackpoint><Time>2026-06-20T%sZ</Time><DistanceMeters>9999</DistanceMeters>
                        <Position><LatitudeDegrees>%s</LatitudeDegrees><LongitudeDegrees>4.85</LongitudeDegrees></Position>
                        <HeartRateBpm><Value>%d</Value></HeartRateBpm></Trackpoint>
                      <Trackpoint><Time>2026-06-20T%sZ</Time><DistanceMeters>9999</DistanceMeters>
                        <Position><LatitudeDegrees>%s</LatitudeDegrees><LongitudeDegrees>4.85</LongitudeDegrees></Position>
                        <HeartRateBpm><Value>%d</Value></HeartRateBpm></Trackpoint>
                    </Track>
                  </Lap>
                """.formatted(start, seconds, meters, hr, start, fromLat, hr, end, toLat, hr);
    }

    @Test
    void unTcxDeFractionneSeRelitTourParTour() throws Exception {
        MockMvc mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        String athlete = signUpAthlete(mvc);

        String activityId = objectMapper.readTree(mvc.perform(
                        multipart("/me/activities/import-file")
                                .file(new MockMultipartFile("file", "seance.tcx", "application/xml",
                                        TCX.getBytes(StandardCharsets.UTF_8)))
                                .header("Authorization", athlete))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString())
                .get("id").asText();

        mvc.perform(get("/me/activities/{id}/laps", activityId).header("Authorization", athlete))
                .andExpect(status().isOk())
                // DEVICE : ce sont les tours de la montre, pas une découpe kilométrique.
                .andExpect(jsonPath("$.kind").value("DEVICE"))
                .andExpect(jsonPath("$.laps.length()").value(4))
                .andExpect(jsonPath("$.laps[0].index").value(1))
                .andExpect(jsonPath("$.laps[0].distanceM").value(400))
                .andExpect(jsonPath("$.laps[0].durationS").value(84))
                // 400 m en 84 s → 3'30/km, calculé côté serveur pour que tous les écrans s'accordent.
                .andExpect(jsonPath("$.laps[0].paceSPerKm").value(210))
                .andExpect(jsonPath("$.laps[0].avgHr").value(168))
                .andExpect(jsonPath("$.laps[1].distanceM").value(200))
                .andExpect(jsonPath("$.laps[3].index").value(4));
    }

    /** Une saisie à la main n'a ni tours ni flux : l'écran doit le dire, pas inventer. */
    @Test
    void uneSaisieManuelleNaAucunTour() throws Exception {
        MockMvc mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        String athlete = signUpAthlete(mvc);

        String activityId = objectMapper.readTree(mvc.perform(post("/me/activities")
                        .header("Authorization", athlete).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"activityDate\":\"2026-06-21\",\"title\":\"Footing\","
                                + "\"distanceM\":8000,\"durationS\":2700}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString())
                .get("id").asText();

        mvc.perform(get("/me/activities/{id}/laps", activityId).header("Authorization", athlete))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kind").value("SPLIT"))
                .andExpect(jsonPath("$.laps.length()").value(0));
    }

    /** Athlète inscrit par invitation, comme dans la vraie vie : coach → invitation → compte. */
    private String signUpAthlete(MockMvc mvc) throws Exception {
        JsonNode auth = objectMapper.readTree(mvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"laps-%s@test.fr","password":"password123","fullName":"C","termsAccepted": true, "clubName":"LAP %s"}
                                """.formatted(UUID.randomUUID(), UUID.randomUUID())))
                .andReturn().getResponse().getContentAsString());
        String coach = "Bearer " + auth.get("accessToken").asText();
        String clubId = auth.get("user").get("clubId").asText();
        verifyCoachEmail(clubId);

        String email = "lapathlete-" + UUID.randomUUID() + "@darilab.app";
        String athleteId = objectMapper.readTree(mvc.perform(post("/clubs/{c}/athletes", clubId)
                        .header("Authorization", coach).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"Léa\",\"lastName\":\"Run\",\"email\":\"" + email + "\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString())
                .get("id").asText();

        String url = objectMapper.readTree(mvc.perform(post("/clubs/{c}/athletes/{a}/invitation", clubId, athleteId)
                        .header("Authorization", coach))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString())
                .get("inviteUrl").asText();
        String token = url.substring(url.lastIndexOf('/') + 1);

        mvc.perform(post("/public/invitations/{t}/accept", token).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"healthDataConsent\":true,\"termsAccepted\":true,"
                                + "\"email\":\"" + email + "\",\"password\":\"athletepass1\"}"))
                .andExpect(status().isOk());

        return "Bearer " + objectMapper.readTree(mvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"athletepass1\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString())
                .get("accessToken").asText();
    }

    private void verifyCoachEmail(String clubId) {
        com.coachrun.entity.User user = userRepository.findAll().stream()
                .filter(u -> u.getClub() != null && clubId.equals(u.getClub().getId().toString()))
                .filter(u -> u.getRole() == com.coachrun.entity.enums.UserRole.HEAD_COACH)
                .findFirst().orElseThrow();
        user.setEmailVerified(true);
        userRepository.saveAndFlush(user);
    }
}
