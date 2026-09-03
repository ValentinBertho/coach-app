package com.coachrun;

import jakarta.servlet.DispatcherType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Les dispatchs internes au conteneur (ASYNC, ERROR) ne repassent pas par l'autorisation.
 *
 * <p><b>Ce que cela protège.</b> Un flux SSE se ferme par un dispatch asynchrone, sur une réponse
 * committée depuis son premier événement. Y refuser l'accès — ce qui arrive dès que le jeton
 * porté en paramètre a expiré pendant que le flux courait, quinze minutes de validité pour une
 * demi-heure de flux — produit un 401 impossible à écrire, puis une page d'erreur qui échoue à
 * son tour. Quatre lignes ERROR par fermeture, sans que personne ne voie rien, et des milliers
 * par jour derrière un relais qui coupe les connexions longues.</p>
 *
 * <p>Reproduit hors suite sur l'application réelle (jeton ramené à cinq secondes, flux fermé par
 * le plafond par utilisateur) : les quatre lignes de production apparaissent à l'identique sans
 * ce réglage, et disparaissent avec. Rejouer le filtre JWT sur le dispatch asynchrone ne suffit
 * pas — vérifié aussi : le jeton étant expiré, il n'a plus rien à reposer.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
class AsyncDispatchAuthorizationTest {

    @Autowired private WebApplicationContext context;

    private MockMvc mockMvc() {
        return MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void asyncDispatchIsNotReauthorized() throws Exception {
        mockMvc().perform(get("/error").with(request -> {
            request.setDispatcherType(DispatcherType.ASYNC);
            return request;
        })).andExpect(status().is(not401()));
    }

    @Test
    void errorDispatchIsNotReauthorized() throws Exception {
        mockMvc().perform(get("/error").with(request -> {
            request.setDispatcherType(DispatcherType.ERROR);
            return request;
        })).andExpect(status().is(not401()));
    }

    /**
     * Le pendant indispensable : ce qu'un client peut réellement émettre — un dispatch REQUEST —
     * reste soumis à l'authentification. Sans cette vérification, le réglage ci-dessus pourrait
     * dériver en porte ouverte sans que rien ne le signale.
     */
    @Test
    void plainRequestToTheSamePathStillRequiresAuthentication() throws Exception {
        mockMvc().perform(get("/error")).andExpect(status().isUnauthorized());
    }

    @Test
    void protectedRouteStillRequiresAuthentication() throws Exception {
        mockMvc().perform(get("/notifications/unread-count")).andExpect(status().isUnauthorized());
    }

    private static org.hamcrest.Matcher<Integer> not401() {
        return org.hamcrest.Matchers.not(401);
    }
}
