package com.coachrun;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * « Quelle version tourne ? » — la réponse, sur une route publique.
 *
 * <p><b>Ce que cela ferme.</b> Une erreur remontée par un utilisateur portait un identifiant de
 * corrélation, une heure et un écran, mais rien qui désigne le <i>code</i> qui l'avait produite.
 * La version applicative existait bien quelque part, jamais reliée à un commit : entre deux
 * déploiements du même 0.3.0, « ça marchait hier » restait indécidable. C'est le point OPS-09 du
 * plan de conformité.</p>
 *
 * <p>La route est publique et sans jeton, délibérément : la question se pose avant d'avoir un
 * accès — au support, à un utilisateur au téléphone, à une sonde d'exploitation.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
// Le réglage ci-dessous force un contexte Spring dédié : sur la base mémoire partagée par le
// reste de la suite, Liquibase rejouerait ses migrations sur un schéma déjà migré et le contexte
// refuserait de démarrer (« Table "databasechangelog" already exists »). D'où une base à part —
// même précaution que StravaControllerTest.
@TestPropertySource(properties = {
        "app.build.commit=abcdef1234567890",
        "spring.datasource.url=jdbc:h2:mem:version-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
})
class DeployedVersionTest {

    @Autowired private WebApplicationContext context;
    @Autowired private ObjectMapper objectMapper;

    private MockMvc mockMvc() {
        return MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void theDeployedVersionIsReadableWithoutAToken() throws Exception {
        String body = mockMvc().perform(get("/actuator/info"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        JsonNode app = objectMapper.readTree(body).get("app");
        assertThat(app).as("/actuator/info porte un bloc « app »").isNotNull();
        assertThat(app.get("version").asText()).isNotBlank();
        assertThat(app.get("environment").asText()).isNotBlank();
    }

    /**
     * Le commit posé par la plateforme de déploiement l'emporte, et il est abrégé comme le fait
     * Git : c'est ce qu'on recopie dans une recherche.
     */
    @Test
    void theCommitComesFromTheDeploymentPlatform() throws Exception {
        String body = mockMvc().perform(get("/actuator/info"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        assertThat(objectMapper.readTree(body).get("app").get("commit").asText())
                .isEqualTo("abcdef1");
    }
}
