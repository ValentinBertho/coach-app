package com.coachrun.security;

import com.coachrun.entity.User;
import com.coachrun.entity.enums.UserRole;
import com.coachrun.repository.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/**
 * Vérification d'e-mail exigée pour les actions qui font <strong>sortir</strong> un e-mail de la
 * plateforme vers un tiers choisi par l'utilisateur — inviter un athlète, inviter un coach.
 *
 * <p>La vérification reste non bloquante pour le reste : un coach qui vient de s'inscrire garde
 * l'accès complet en lecture et peut travailler. Mais tant qu'il n'a pas prouvé qu'il contrôle
 * son adresse, il ne peut pas se servir de Darilab pour écrire à d'autres gens — c'est ce qui
 * transforme un produit en relais de spam et brûle le domaine d'envoi.</p>
 *
 * <p>S'utilise dans un {@code @PreAuthorize} :
 * {@code @PreAuthorize("@emailVerificationValidator.isVerified(authentication)")}.</p>
 */
@Component("emailVerificationValidator")
public class EmailVerificationValidator {

    private final UserRepository userRepository;

    public EmailVerificationValidator(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public boolean isVerified(Authentication authentication) {
        if (authentication == null
                || !(authentication.getPrincipal() instanceof AuthPrincipal principal)) {
            return false;
        }
        if (principal.role() == UserRole.PLATFORM_ADMIN) {
            return true;
        }
        return userRepository.findById(principal.userId())
                .map(User::isEmailVerified)
                .orElse(false);
    }
}
