package com.coachrun;

import com.coachrun.entity.User;
import com.coachrun.repository.UserRepository;
import com.coachrun.security.JwtService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.context.ApplicationContext;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Pour les tests qui se connectent en administrateur sans porter sur la connexion elle-même : la
 * connexion par mot de passe ne rend plus qu'un défi de double authentification. Ce helper vérifie
 * que c'est bien un défi, puis émet directement le jeton d'accès.
 *
 * <p>Pourquoi ne pas relever le défi par HTTP : la vérification tourne dans sa propre transaction,
 * qui ne voit pas le défi écrit dans la transaction d'un test {@code @Transactional}. Le parcours
 * complet est couvert par {@link AdminMfaTest}.</p>
 */
final class MfaTestSupport {

    private MfaTestSupport() {
    }

    /** Réponse de connexion → réponse avec jeton d'accès (inchangée si aucun second facteur n'était demandé). */
    static JsonNode complete(MockMvc mvc, ApplicationContext context, String email, JsonNode login) {
        JsonNode mfa = login.get("mfa");
        if (mfa == null || mfa.isNull()) {
            return login;
        }
        if (login.hasNonNull("accessToken")) {
            throw new AssertionError("un défi de double authentification ne doit porter aucun jeton");
        }
        User user = context.getBean(UserRepository.class).findByEmailIgnoreCase(email).orElseThrow();
        ObjectNode out = context.getBean(ObjectMapper.class).createObjectNode();
        out.put("accessToken", context.getBean(JwtService.class).generateAccessToken(user));
        return out;
    }
}
