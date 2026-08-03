package com.coachrun.config;

import com.coachrun.entity.User;
import com.coachrun.entity.enums.UserRole;
import com.coachrun.entity.enums.UserStatus;
import com.coachrun.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;

/**
 * Création du compte {@code PLATFORM_ADMIN} en production.
 *
 * <p><b>Pourquoi ce fichier existe.</b> Le seul endroit du code qui créait un administrateur
 * plateforme était le jeu de démonstration ({@code DemoSeedService}), lui-même réservé au profil
 * {@code dev} et désactivé en production ({@code app.seed.enabled: false}). Aucune migration
 * n'en insérait, aucune route ne permettait d'en promouvoir un — et
 * {@code AdminUserService.update()} ne peut changer un rôle que depuis un compte déjà
 * administrateur. L'instance de production n'avait donc <b>aucun</b> accès à son back-office,
 * ce qui rendait inatteignables : la révocation d'invitation (seul chemin du produit), la
 * suppression d'un compte coach (seul chemin pour honorer une demande d'effacement RGPD, que la
 * politique de confidentialité promet pourtant par e-mail), les statistiques plateforme et toute
 * la gestion des clubs. Le premier ticket de support l'aurait découvert.</p>
 *
 * <p><b>Comment ça marche.</b> Deux variables d'environnement, lues au démarrage :</p>
 * <pre>
 * PLATFORM_ADMIN_EMAIL=admin@darilab.app
 * PLATFORM_ADMIN_PASSWORD=&lt;mot de passe fort, gestionnaire de mots de passe&gt;
 * </pre>
 *
 * <p><b>Idempotent et non destructeur.</b> Si le compte existe déjà, rien n'est touché — ni le
 * mot de passe, ni le rôle. C'est délibéré : un redéploiement ne doit jamais réinitialiser
 * l'accès administrateur à la valeur d'une variable d'environnement, sinon la rotation du mot de
 * passe depuis l'application serait annulée au déploiement suivant, et quiconque lit les
 * variables Railway reprendrait la main. Pour repartir de zéro, on supprime le compte en base.</p>
 *
 * <p><b>Silencieux si non configuré.</b> Sans les deux variables, l'application démarre
 * normalement en le signalant. Ce n'est volontairement pas un garde-fou bloquant comme
 * {@code StartupSecretsValidator} : une instance sans administrateur est diminuée, pas
 * dangereuse — et bloquer le démarrage sur ce point empêcherait de déployer un correctif urgent.</p>
 */
@Slf4j
@Configuration
@Profile("prod")
public class PlatformAdminBootstrap {

    /** Longueur minimale du mot de passe initial : ce compte voit tous les clubs. */
    private static final int MIN_PASSWORD_LENGTH = 12;

    @Bean
    ApplicationRunner platformAdminRunner(UserRepository userRepository,
                                          PasswordEncoder passwordEncoder,
                                          @Value("${app.admin.email:}") String email,
                                          @Value("${app.admin.password:}") String password) {
        return args -> ensureAdmin(userRepository, passwordEncoder, email, password);
    }

    @Transactional
    void ensureAdmin(UserRepository userRepository, PasswordEncoder passwordEncoder,
                     String email, String password) {
        if (!StringUtils.hasText(email) || !StringUtils.hasText(password)) {
            log.warn("Aucun administrateur plateforme configuré (PLATFORM_ADMIN_EMAIL / "
                    + "PLATFORM_ADMIN_PASSWORD) : le back-office /admin est inaccessible sur cette "
                    + "instance — ni révocation d'invitation, ni suppression de compte coach.");
            return;
        }
        if (password.length() < MIN_PASSWORD_LENGTH) {
            log.error("PLATFORM_ADMIN_PASSWORD trop court ({} caractères, {} minimum) : "
                    + "administrateur non créé.", password.length(), MIN_PASSWORD_LENGTH);
            return;
        }

        String normalized = email.trim().toLowerCase(java.util.Locale.ROOT);
        if (userRepository.existsByEmailIgnoreCase(normalized)) {
            log.info("Administrateur plateforme déjà présent — inchangé.");
            return;
        }

        User admin = new User();
        admin.setEmail(normalized);
        admin.setPasswordHash(passwordEncoder.encode(password));
        admin.setFullName("Administrateur plateforme");
        admin.setRole(UserRole.PLATFORM_ADMIN);
        admin.setStatus(UserStatus.ACTIVE);
        // Compte de service créé par l'exploitant : l'adresse est vérifiée par construction, et
        // les CGU n'ont pas de sens pour l'éditeur lui-même — mais on horodate pour que la
        // colonne reste non nulle sur tous les comptes actifs.
        admin.setEmailVerified(true);
        admin.setTermsAcceptedAt(Instant.now());
        userRepository.save(admin);

        log.warn("Administrateur plateforme créé ({}). Changez ce mot de passe depuis "
                + "l'application dès la première connexion.", normalized);
    }
}
