package com.coachrun.security;

import com.coachrun.repository.UserRepository;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/**
 * Un jeton émis avant le dernier changement de mot de passe est périmé.
 *
 * <p>Sans ce contrôle, réinitialiser son mot de passe ne mettait fin à aucune session : les
 * refresh tokens déjà émis (TTL 30 jours) restaient valables, y compris celui de la personne
 * dont on cherchait justement à se débarrasser. Le blacklist ne couvre que les jetons qu'on
 * connaît — pas ceux d'un attaquant.</p>
 *
 * <p>Une seule colonne est lue (index primaire) : le coût est celui d'un {@code select} sur la
 * clé, et l'immense majorité des comptes n'ont jamais changé de mot de passe (valeur nulle,
 * aucun jeton invalidé).</p>
 */
@Component
@RequiredArgsConstructor
public class TokenFreshnessValidator {

    private final UserRepository userRepository;

    /** Vrai si le jeton a été émis avant le dernier changement de mot de passe du compte. */
    public boolean isStale(UUID userId, Instant issuedAt) {
        if (userId == null || issuedAt == null) {
            return false;
        }
        Instant changedAt = userRepository.findPasswordChangedAt(userId).orElse(null);
        // Le « iat » d'un JWT est à la seconde : un jeton émis dans la même seconde que le
        // changement est considéré comme postérieur (c'est celui que le changement vient d'émettre).
        return changedAt != null && issuedAt.isBefore(changedAt.truncatedTo(java.time.temporal.ChronoUnit.SECONDS));
    }

    /** Variante pour un jeton déjà décodé. */
    public boolean isStale(Claims claims) {
        if (claims.getSubject() == null || claims.getIssuedAt() == null) {
            return false;
        }
        return isStale(UUID.fromString(claims.getSubject()), claims.getIssuedAt().toInstant());
    }
}
