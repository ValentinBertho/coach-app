package com.coachrun.service;

import com.coachrun.dto.request.LoginRequest;
import com.coachrun.dto.request.RefreshRequest;
import com.coachrun.dto.request.RegisterRequest;
import com.coachrun.dto.response.AuthResponse;
import com.coachrun.dto.response.UserResponse;
import com.coachrun.entity.Athlete;
import com.coachrun.entity.Club;
import com.coachrun.entity.User;
import com.coachrun.entity.enums.AthleteStatus;
import com.coachrun.entity.enums.ClubStatus;
import com.coachrun.entity.enums.UserRole;
import com.coachrun.entity.enums.UserStatus;
import com.coachrun.exception.ConflictException;
import com.coachrun.exception.NotFoundException;
import com.coachrun.exception.UnauthorizedException;
import com.coachrun.repository.AthleteRepository;
import com.coachrun.repository.ClubRepository;
import com.coachrun.repository.UserRepository;
import com.coachrun.security.JwtService;
import com.coachrun.util.SlugUtil;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Authentification : inscription d'un coach (compte HEAD_COACH + club implicite),
 * connexion et rafraîchissement de jeton. Stateless (JWT).
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {

    private final UserRepository userRepository;
    private final ClubRepository clubRepository;
    private final AthleteRepository athleteRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmailIgnoreCase(request.email())) {
            throw new ConflictException("Un compte existe déjà avec cet email.");
        }

        Club club = new Club();
        club.setName(request.clubName());
        club.setSlug(uniqueSlug(request.clubName()));
        club.setStatus(ClubStatus.ACTIVE);
        club = clubRepository.save(club);

        User user = new User();
        user.setEmail(request.email().toLowerCase());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setFullName(request.fullName());
        user.setRole(UserRole.HEAD_COACH);
        user.setStatus(UserStatus.ACTIVE);
        user.setClub(club);
        user = userRepository.save(user);

        log.info("Nouveau coach inscrit (club={})", club.getId());
        return toAuthResponse(user);
    }

    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmailIgnoreCase(request.email())
                .orElseThrow(() -> new UnauthorizedException("Email ou mot de passe incorrect."));

        if (user.getPasswordHash() == null
                || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new UnauthorizedException("Email ou mot de passe incorrect.");
        }
        if (user.getStatus() == UserStatus.SUSPENDED) {
            throw new UnauthorizedException("Ce compte est suspendu.");
        }
        return toAuthResponse(user);
    }

    public AuthResponse refresh(RefreshRequest request) {
        final Claims claims;
        try {
            claims = jwtService.parse(request.refreshToken());
        } catch (RuntimeException ex) {
            throw new UnauthorizedException("Jeton de rafraîchissement invalide ou expiré.");
        }
        if (!JwtService.TYPE_REFRESH.equals(claims.get("typ", String.class))) {
            throw new UnauthorizedException("Jeton de rafraîchissement invalide.");
        }
        User user = userRepository.findById(UUID.fromString(claims.getSubject()))
                .orElseThrow(() -> new UnauthorizedException("Compte introuvable."));
        return toAuthResponse(user);
    }

    /**
     * Onboarding athlète par lien magique : valide le token, crée (ou réutilise) le compte
     * ATHLETE rattaché à l'athlète, puis émet les jetons. Sans mot de passe.
     */
    @Transactional
    public AuthResponse acceptInvitation(String token, boolean healthDataConsent) {
        Athlete athlete = athleteRepository.findByInviteToken(token)
                .filter(a -> a.getInviteExpiresAt() != null
                        && a.getInviteExpiresAt().isAfter(java.time.Instant.now()))
                .orElseThrow(() -> new NotFoundException("Invitation invalide ou expirée."));

        if (healthDataConsent && athlete.getHealthDataConsentAt() == null) {
            athlete.setHealthDataConsentAt(java.time.Instant.now());
        }

        User user = userRepository.findByAthleteId(athlete.getId()).orElseGet(() -> {
            User u = new User();
            u.setEmail("ath-" + athlete.getId() + "@athlete.coachrun.local");
            u.setFullName(athlete.getFirstName() + " " + athlete.getLastName());
            u.setRole(UserRole.ATHLETE);
            u.setStatus(UserStatus.ACTIVE);
            u.setClub(athlete.getClub());
            u.setAthlete(athlete);
            return userRepository.save(u);
        });

        athlete.setStatus(AthleteStatus.ACTIVE);
        athlete.setInviteToken(null);
        athlete.setInviteExpiresAt(null);
        return toAuthResponse(user);
    }

    public UserResponse currentUser(UUID userId) {
        return userRepository.findById(userId)
                .map(UserResponse::from)
                .orElseThrow(() -> new UnauthorizedException("Session invalide."));
    }

    private AuthResponse toAuthResponse(User user) {
        return new AuthResponse(
                jwtService.generateAccessToken(user),
                jwtService.generateRefreshToken(user),
                jwtService.getAccessTtlSeconds(),
                UserResponse.from(user));
    }

    private String uniqueSlug(String clubName) {
        String base = SlugUtil.slugify(clubName);
        String slug = base;
        int i = 1;
        while (clubRepository.existsBySlug(slug)) {
            slug = base + "-" + (++i);
        }
        return slug;
    }
}
