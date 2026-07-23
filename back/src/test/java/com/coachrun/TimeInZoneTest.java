package com.coachrun;

import com.coachrun.service.DemoSeedService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * V2-7 — temps-en-zone réalisé : import d'un TCX avec FC + positions, puis répartition du temps
 * par zone (allure + FC) calculée depuis le flux et les zones de l'athlète.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class TimeInZoneTest {

    @Autowired private WebApplicationContext context;
    @Autowired private DemoSeedService demoSeedService;
    @Autowired private ObjectMapper objectMapper;

    private MockMvc mvc;
    private String bearer;
    private String clubId;
    private String athleteId;

    @BeforeEach
    void setUp() throws Exception {
        demoSeedService.seed();
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        JsonNode auth = objectMapper.readTree(mvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + DemoSeedService.HEAD_COACH_EMAIL
                                + "\",\"password\":\"" + DemoSeedService.DEMO_PASSWORD + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        bearer = "Bearer " + auth.get("accessToken").asText();
        clubId = auth.get("user").get("clubId").asText();
        JsonNode athletes = objectMapper.readTree(mvc.perform(
                        get("/clubs/{c}/athletes?size=50", clubId).header("Authorization", bearer))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        athleteId = athletes.get("content").get(0).get("id").asText();
        // Ancres → les zones FC/allure de l'athlète sont pré-remplies.
        mvc.perform(put("/clubs/{c}/athletes/{a}/physio", clubId, athleteId)
                        .header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lt1Ms\":3.5,\"lt2Ms\":3.9,\"vcMs\":4.2,\"fcLt1\":148,\"fcLt2\":163,\"fcMax\":185}"))
                .andExpect(status().isOk());
    }

    @Test
    void computesTimeInZoneFromImportedStream() throws Exception {
        // GPX : 7 points à 30 s d'intervalle, ~111 m chacun (≈ 4:30/km), FC 140→168 (extensions).
        int[] hrs = {140, 150, 158, 163, 168, 155, 150};
        StringBuilder gpx = new StringBuilder("""
                <?xml version="1.0"?>
                <gpx><trk><trkseg>
                """);
        double lat = 45.7600;
        for (int i = 0; i < hrs.length; i++) {
            String time = String.format("2026-07-01T08:%02d:%02dZ", (i * 30) / 60, (i * 30) % 60);
            gpx.append("<trkpt lat=\"").append(lat + i * 0.001).append("\" lon=\"4.8357\">")
                    .append("<ele>170</ele><time>").append(time).append("</time>")
                    .append("<extensions><gpxtpx:TrackPointExtension><gpxtpx:hr>")
                    .append(hrs[i]).append("</gpxtpx:hr></gpxtpx:TrackPointExtension></extensions>")
                    .append("</trkpt>\n");
        }
        gpx.append("</trkseg></trk></gpx>");

        MockMultipartFile file = new MockMultipartFile("file", "run.gpx", "application/gpx+xml",
                gpx.toString().getBytes(StandardCharsets.UTF_8));
        mvc.perform(multipart("/clubs/{c}/athletes/{a}/activities/import-file", clubId, athleteId)
                        .file(file).header("Authorization", bearer))
                .andExpect(status().isCreated());

        JsonNode activities = objectMapper.readTree(mvc.perform(
                        get("/clubs/{c}/athletes/{a}/activities", clubId, athleteId).header("Authorization", bearer))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        String activityId = activities.get(0).get("id").asText();

        JsonNode tiz = objectMapper.readTree(mvc.perform(
                        get("/clubs/{c}/athletes/{a}/activities/{id}/time-in-zone", clubId, athleteId, activityId)
                                .header("Authorization", bearer))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());

        JsonNode scales = tiz.get("scales");
        assertThat(scales).isNotEmpty();

        // L'échelle FC : temps total > 0 et somme des pourcentages ≈ 100.
        JsonNode hrScale = null;
        for (JsonNode s : scales) if ("HR".equals(s.get("metricCode").asText())) hrScale = s;
        assertThat(hrScale).isNotNull();
        assertThat(hrScale.get("totalS").asInt()).isGreaterThan(0);
        double sumPct = 0;
        for (JsonNode b : hrScale.get("buckets")) sumPct += b.get("pct").asDouble();
        assertThat(sumPct).isBetween(99.0, 101.0);
    }
}
