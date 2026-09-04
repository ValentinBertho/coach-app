package com.coachrun.service;

import com.coachrun.entity.Club;
import com.coachrun.entity.ClubMember;
import com.coachrun.entity.User;
import com.coachrun.entity.enums.ClubRole;
import com.coachrun.entity.enums.ClubStatus;
import com.coachrun.entity.enums.UserRole;
import com.coachrun.entity.enums.UserStatus;
import com.coachrun.exception.ApiException;
import com.coachrun.repository.ClubMemberRepository;
import com.coachrun.repository.ClubRepository;
import com.coachrun.repository.UserRepository;
import com.coachrun.util.SlugUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

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
     * Crée l'espace de travail et son coach propriétaire.
     *
     * @param clubName      nom de la structure, tel que saisi ; peut être vide pour un indépendant,
     *                      auquel cas l'espace prend le nom du coach
     * @param fullName      nom du coach
     * @param email         adresse du coach, déjà contrôlée comme libre par l'appelant
     * @param passwordHash  empreinte du mot de passe choisi, ou d'un secret aléatoire quand le
     *                      coach n'en a pas encore posé (il en choisira un par le lien reçu)
     * @param emailVerified vrai quand l'appelant sait déjà que l'adresse appartient au coach
     * @param soloPractice  vrai pour un coach indépendant : même espace, autre vocabulaire
     * @return le compte propriétaire créé, rattaché à son espace
     */
    @Transactional
    public User openClub(String clubName, String fullName, String email,
                         String passwordHash, boolean emailVerified, boolean soloPractice) {
        String name = workspaceName(clubName, fullName, soloPractice);
        Club club = new Club();
        club.setName(name);
        club.setSlug(uniqueSlug(name));
        club.setStatus(ClubStatus.ACTIVE);
        club.setSoloPractice(soloPractice);
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
     * Le nom de l'espace : celui qu'on a saisi, ou celui du coach.
     *
     * <p>Un indépendant n'a pas de club à nommer. Lui en réclamer un le forçait à en inventer un,
     * ou à y mettre son propre nom — ce que fait désormais ce repli, mais sans le lui demander.
     * Le champ reste offert : beaucoup d'indépendants exercent sous un nom d'activité, et c'est
     * celui-là qu'ils veulent voir.</p>
     *
     * <p>Pour un club, le nom reste exigé. Le refus est un 400 explicite plutôt qu'un
     * {@code @NotBlank} sur le DTO : la règle dépend d'un autre champ de la même requête, et une
     * annotation ne sait pas dire « obligatoire, sauf si ».</p>
     */
    public static String workspaceName(String submitted, String fullName, boolean soloPractice) {
        if (StringUtils.hasText(submitted)) {
            return submitted.trim();
        }
        if (soloPractice) {
            return fullName.trim();
        }
        throw new ApiException(HttpStatus.BAD_REQUEST,
                "Le nom du club est requis. Si vous coachez en indépendant, "
                        + "choisissez « En indépendant » : le nom devient facultatif.");
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
