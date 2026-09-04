package com.coachrun.service;

import com.coachrun.dto.request.AthleteAccountRequest;
import com.coachrun.dto.request.AthleteRegistrationRequest;
import com.coachrun.dto.response.AthleteAccountResponse;
import com.coachrun.dto.response.AuthResponse;
import com.coachrun.entity.AthleteAccount;
import com.coachrun.entity.User;
import com.coachrun.entity.enums.UserRole;
import com.coachrun.entity.enums.UserStatus;
import com.coachrun.exception.ApiException;
import com.coachrun.exception.ConflictException;
import com.coachrun.exception.NotFoundException;
import com.coachrun.repository.AthleteAccountRepository;
import com.coachrun.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.UUID;

/**
 * Le compte d'un athlète qui s'inscrit seul.
 *
 * <h2>Ce que cette inscription ne crée pas</h2>
 *
 * <p>Ni club, ni fiche {@code Athlete}, ni relation. Un athlète qui vient de s'inscrire n'appartient
 * à personne — c'est tout l'objet du hub, et c'est aussi ce qui le distingue d'une invitation, où
 * la fiche préexiste et où le compte ne fait que l'activer. La fiche naîtra à l'acceptation d'une
 * demande de coaching, dans l'espace du coach qui accepte.</p>
 *
 * <h2>L'âge minimum</h2>
 *
 * <p>Seize ans, en dur. Un mineur plus jeune qui s'inscrirait seul consentirait seul au traitement
 * de ses données de santé et solliciterait seul un adulte inconnu : deux choses que la plateforme
 * ne sait pas encadrer. Le chemin reste ouvert par le coach ou le club, qui créent la fiche et
 * invitent — la relation étant alors nouée hors plateforme avec les responsables légaux.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AthleteAccountService {

    /** Âge minimum à l'inscription libre. */
    public static final int MINIMUM_AGE = 16;

    private final AthleteAccountRepository accountRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final NotificationService notificationService;
    private final ClockService clock;
    private final AuthService authService;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    /**
     * Crée le compte d'un athlète qui s'inscrit de lui-même.
     *
     * <p>L'adresse n'est pas encore vérifiée, et c'est volontaire : enfermer quelqu'un dehors le
     * temps qu'il relève ses e-mails ferait perdre l'inscription. La vérification est en revanche
     * exigée <b>avant la première demande de coaching</b> — c'est là qu'elle protège quelqu'un
     * d'autre que lui.</p>
     */
    @Transactional
    public AuthResponse register(AthleteRegistrationRequest request) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        if (userRepository.existsByEmailIgnoreCase(email)) {
            // Message volontairement identique à celui d'un coach : ne pas apprendre à un visiteur
            // quel type de compte existe derrière une adresse.
            throw new ConflictException("Un compte existe déjà avec cet email.");
        }
        requireMinimumAge(request.birthDate());

        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setFullName((request.firstName().trim() + " " + request.lastName().trim()).trim());
        user.setRole(UserRole.ATHLETE);
        user.setStatus(UserStatus.ACTIVE);
        // Ni club ni fiche : cet athlète n'appartient à personne, et c'est le principe même du hub.
        user.setClub(null);
        user.setAthlete(null);
        user.setEmailVerified(false);
        user.setTermsAcceptedAt(Instant.now());
        user.setVerifyToken(UUID.randomUUID().toString().replace("-", ""));
        user.setVerifyExpiresAt(Instant.now().plus(7, ChronoUnit.DAYS));
        user = userRepository.save(user);

        AthleteAccount account = new AthleteAccount();
        account.setUser(user);
        account.setFirstName(request.firstName().trim());
        account.setLastName(request.lastName().trim());
        account.setBirthDate(request.birthDate());
        account.setGoal(trimToNull(request.goal()));
        account.setLookingForCoach(true);
        account.setTermsAcceptedAt(Instant.now());
        account.setHealthDataConsentAt(Instant.now());
        accountRepository.save(account);

        notificationService.notifyEmailVerification(user.getEmail(), user.getFullName(),
                frontendUrl + "/verify-email/" + user.getVerifyToken());
        log.info("Nouvel athlète inscrit librement (user={})", user.getId());
        return authService.tokensFor(user);
    }

    public AthleteAccountResponse myAccount(UUID userId) {
        return AthleteAccountResponse.from(require(userId));
    }

    @Transactional
    public AthleteAccountResponse update(UUID userId, AthleteAccountRequest request) {
        AthleteAccount account = require(userId);
        if (StringUtils.hasText(request.firstName())) {
            account.setFirstName(request.firstName().trim());
        }
        if (StringUtils.hasText(request.lastName())) {
            account.setLastName(request.lastName().trim());
        }
        account.setSex(request.sex());
        account.setDiscipline(request.discipline());
        account.setLevel(request.level());
        account.setCity(trimToNull(request.city()));
        account.setCountry(request.country() == null ? null
                : request.country().trim().toUpperCase(Locale.ROOT));
        account.setGoal(trimToNull(request.goal()));
        account.setLookingForCoach(request.lookingForCoach());

        // Le nom affiché suit celui du compte : sans cela, un athlète qui corrige son nom resterait
        // annoncé sous l'ancien dans les demandes qu'il envoie et les fils qu'il ouvre.
        User user = account.getUser();
        user.setFullName((account.getFirstName() + " " + account.getLastName()).trim());
        return AthleteAccountResponse.from(account);
    }

    /** Le compte de cet utilisateur, ou 404 — un coach n'en a pas, et n'a pas à en avoir. */
    public AthleteAccount require(UUID userId) {
        return accountRepository.findByUserId(userId)
                .orElseThrow(() -> new NotFoundException(
                        "Aucun compte athlète pour cet utilisateur."));
    }

    /**
     * Refuse l'inscription libre en dessous de {@link #MINIMUM_AGE}.
     *
     * <p>Le message nomme le chemin qui reste ouvert. Un refus sec laisserait un adolescent — et
     * souvent son parent, derrière l'écran — sans savoir qu'un club peut l'inscrire.</p>
     */
    private void requireMinimumAge(LocalDate birthDate) {
        if (Period.between(birthDate, clock.today()).getYears() < MINIMUM_AGE) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "L'inscription directe est réservée aux " + MINIMUM_AGE + " ans et plus. "
                            + "En dessous, c'est votre club ou votre coach qui crée votre accès : "
                            + "demandez-lui de vous inviter.");
        }
    }

    private static String trimToNull(String s) {
        return StringUtils.hasText(s) ? s.trim() : null;
    }
}
