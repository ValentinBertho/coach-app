package com.coachrun.service;

import com.coachrun.entity.Club;
import com.coachrun.entity.ClubMember;
import com.coachrun.entity.User;
import com.coachrun.entity.enums.ClubRole;
import com.coachrun.entity.enums.ClubStatus;
import com.coachrun.entity.enums.UserRole;
import com.coachrun.entity.enums.UserStatus;
import com.coachrun.repository.ClubMemberRepository;
import com.coachrun.repository.ClubRepository;
import com.coachrun.repository.UserRepository;
import com.coachrun.util.SlugUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.UUID;

/**
 * Ouverture d'un club et de son compte propriétaire.
 *
 * <h2>Pourquoi ce service existe</h2>
 *
 * <p>Le geste — créer le club, son coach propriétaire, le rattachement {@code OWNER} et le jeu de
 * séances de départ — vivait à l'intérieur de {@code AuthService.register}. Il a désormais deux
 * appelants : l'inscription directe (mode {@code open} / {@code invite}) et la validation d'une
 * demande par un administrateur (mode {@code request}). Recopié, il aurait divergé au premier
 * changement — un club ouvert par validation se serait retrouvé sans rattachement {@code OWNER},
 * ou sans bibliothèque, et personne ne l'aurait vu avant qu'un coach ne s'en plaigne.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ClubProvisioningService {

    private final UserRepository userRepository;
    private final ClubRepository clubRepository;
    private final ClubMemberRepository clubMemberRepository;
    private final ClubStarterKitService starterKitService;

    /**
     * Crée le club et son coach propriétaire.
     *
     * @param clubName      nom du club, tel que saisi
     * @param fullName      nom du coach
     * @param email         adresse du coach, déjà contrôlée comme libre par l'appelant
     * @param passwordHash  empreinte du mot de passe choisi, ou d'un secret aléatoire quand le
     *                      coach n'en a pas encore posé (il en choisira un par le lien reçu)
     * @param emailVerified vrai quand l'appelant sait déjà que l'adresse appartient au coach
     * @return le compte propriétaire créé, rattaché à son club
     */
    @Transactional
    public User openClub(String clubName, String fullName, String email,
                         String passwordHash, boolean emailVerified) {
        Club club = new Club();
        club.setName(clubName.trim());
        club.setSlug(uniqueSlug(clubName));
        club.setStatus(ClubStatus.ACTIVE);
        club = clubRepository.save(club);

        User user = new User();
        user.setEmail(email.trim().toLowerCase());
        user.setPasswordHash(passwordHash);
        user.setFullName(fullName.trim());
        user.setRole(UserRole.HEAD_COACH);
        user.setStatus(UserStatus.ACTIVE);
        user.setClub(club);
        user.setEmailVerified(emailVerified);
        // Preuve de consentement RGPD : la case a été cochée, à l'inscription comme au dépôt
        // de la demande.
        user.setTermsAcceptedAt(Instant.now());
        user = userRepository.save(user);

        // Le créateur du club en est le propriétaire (membership multi-coach).
        ClubMember owner = new ClubMember();
        owner.setClub(club);
        owner.setCoach(user);
        owner.setClubRole(ClubRole.OWNER);
        owner.setActive(true);
        clubMemberRepository.save(owner);

        installStarterKit(club.getId());
        return user;
    }

    /**
     * Pose le jeu de départ du club — <b>après le commit, et sans jamais faire échouer
     * l'ouverture</b>.
     *
     * <p>Deux raisons de ne pas l'inclure dans la transaction. La première est de principe :
     * ouvrir un club est l'opération la moins remplaçable du produit, et une bibliothèque
     * d'exemple est un agrément — un défaut dans dix modèles de séance ne doit pas empêcher un
     * coach d'avoir un espace. La seconde est pratique : le jeu de départ écrit une trentaine de
     * lignes, ce qui rallongerait d'autant une transaction tenue pendant qu'on attend.</p>
     *
     * <p>Conséquence assumée : sur l'échec, le coach arrive dans une bibliothèque vide — l'état
     * d'avant. C'est journalisé et remonté à Sentry, et le jeu reste installable après coup
     * puisqu'il est idempotent.</p>
     */
    private void installStarterKit(UUID clubId) {
        Runnable install = () -> {
            try {
                starterKitService.install(clubId);
            } catch (RuntimeException ex) {
                log.error("Jeu de départ non installé pour le club {} — l'ouverture reste valide",
                        clubId, ex);
                io.sentry.Sentry.captureException(ex);
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    install.run();
                }
            });
            return;
        }
        install.run();
    }

    /** Un identifiant d'URL unique, suffixé tant qu'il est déjà pris. */
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
