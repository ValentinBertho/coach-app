package com.coachrun.service;

import com.coachrun.entity.Athlete;
import com.coachrun.entity.AthleteAccount;
import com.coachrun.entity.Club;
import com.coachrun.entity.DeviceConnection;
import com.coachrun.entity.User;
import com.coachrun.entity.enums.AthleteStatus;
import com.coachrun.entity.enums.ClubStatus;
import com.coachrun.repository.AthleteRepository;
import com.coachrun.repository.ClubRepository;
import com.coachrun.repository.DeviceConnectionRepository;
import com.coachrun.util.SlugUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.UUID;

/**
 * L'espace personnel de l'athlète : de quoi s'entraîner sans coach.
 *
 * <h2>Pourquoi un espace, et pas une fiche sans club</h2>
 *
 * <p>Toute la surface d'entraînement (calendrier, séance du jour, montre, progrès) suppose une
 * fiche, et toutes les requêtes de la plateforme scopent la fiche par club. Rendre
 * {@code athletes.club_id} nullable aurait touché la moitié du code ; donner à chaque athlète un
 * espace à lui n'en touche aucune. L'espace est marqué {@code personal} : le back-office le tient
 * à l'écart, et personne d'autre que l'athlète n'y a accès — il n'a ni coach ni membre.</p>
 *
 * <h2>Le va-et-vient avec un coach</h2>
 *
 * <p>Quand un coach accepte l'athlète, sa fiche de coaching naît dans l'espace du coach, et le
 * compte s'y rattache. Les montres suivent, pour que les sorties arrivent chez le coach. Quand le
 * coaching se termine, le compte revient ici — à sa fiche, son historique d'avant, ses
 * objectifs.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PersonalSpaceService {

    private final ClubRepository clubRepository;
    private final AthleteRepository athleteRepository;
    private final DeviceConnectionRepository connectionRepository;
    private final ClubStarterKitService starterKitService;
    private final com.coachrun.repository.ActivityRepository activityRepository;
    private final AthleteCarryOverService carryOver;

    /** La fiche personnelle du compte, créée si elle n'existe pas encore. */
    @Transactional
    public Athlete ensure(AthleteAccount account) {
        return athleteRepository.findFirstByAccountIdAndClubPersonalTrue(account.getId())
                .orElseGet(() -> create(account));
    }

    /**
     * Rattache le compte à son espace personnel quand il n'a nulle part où aller : à
     * l'inscription, et au retour d'un coaching.
     */
    @Transactional
    public Athlete attach(User user, AthleteAccount account) {
        Athlete personal = ensure(account);
        user.setAthlete(personal);
        user.setClub(personal.getClub());
        return personal;
    }

    /** La fiche est-elle celle d'un espace personnel ? */
    public static boolean isPersonal(Athlete athlete) {
        return athlete != null && athlete.getClub() != null && athlete.getClub().isPersonal();
    }

    /**
     * Fait suivre les montres d'une fiche à l'autre.
     *
     * <p>Une connexion est attachée à une fiche, et c'est la fiche qui reçoit les sorties. Quand un
     * coach accepte l'athlète, les sorties doivent arriver chez lui : la connexion est déplacée,
     * sans rien redemander au fournisseur — les jetons restent valides. Une connexion déjà
     * présente sur la fiche d'arrivée pour le même fournisseur l'emporte, et celle de la fiche
     * quittée est retirée.</p>
     */
    @Transactional
    public void moveDevices(Athlete from, Athlete to) {
        if (from == null || to == null || from.getId().equals(to.getId())) {
            return;
        }
        for (DeviceConnection conn : connectionRepository.findByAthleteId(from.getId())) {
            if (connectionRepository.findByAthleteIdAndProvider(to.getId(), conn.getProvider()).isPresent()) {
                // Laissée sur la fiche quittée, elle continuerait d'y importer les sorties.
                connectionRepository.delete(conn);
                continue;
            }
            conn.setAthlete(to);
            connectionRepository.save(conn);
        }
    }

    /**
     * L'athlète garde une copie de ce qu'il a fait pendant son coaching.
     *
     * <p>La fiche reste chez le coach, qui garde son suivi ; l'athlète, lui, repartait avec un
     * historique vide. Ses sorties — importées de sa montre ou saisies par lui — sont recopiées
     * dans son espace personnel. Une sortie déjà présente (même source, même identifiant) n'est pas
     * dupliquée. Les séances passées l'accompagnent, avec ses retours, pour que son calendrier ne
     * reparte pas vide ; une sortie rapprochée d'une séance le reste sur la copie. Les séances à
     * venir ne suivent pas.</p>
     *
     * @return nombre de sorties recopiées
     */
    @Transactional
    public int copyHistory(Athlete from, Athlete to) {
        if (from == null || to == null || from.getId().equals(to.getId())) {
            return 0;
        }
        java.util.Map<UUID, UUID> workouts = carryOver.copyPastWorkouts(from, to);
        int copied = 0;
        for (com.coachrun.entity.Activity src : activityRepository.findByAthleteIdOrderByActivityDateDesc(from.getId())) {
            if (src.getExternalId() != null && src.getSource() != null
                    && activityRepository.existsByAthleteIdAndSourceAndExternalId(to.getId(), src.getSource(), src.getExternalId())) {
                continue;
            }
            com.coachrun.entity.Activity copy = new com.coachrun.entity.Activity();
            org.springframework.beans.BeanUtils.copyProperties(src, copy,
                    "id", "createdAt", "updatedAt", "version", "club", "athlete",
                    "matchedWorkoutId", "manualMatch", "status");
            copy.setClub(to.getClub());
            copy.setAthlete(to);
            UUID matched = src.getMatchedWorkoutId() == null ? null : workouts.get(src.getMatchedWorkoutId());
            if (matched != null) {
                copy.setMatchedWorkoutId(matched);
                copy.setManualMatch(src.isManualMatch());
                copy.setStatus(src.getStatus());
            } else {
                copy.setStatus(com.coachrun.entity.enums.ActivityStatus.IMPORTED);
            }
            activityRepository.save(copy);
            copied++;
        }
        return copied;
    }

    private Athlete create(AthleteAccount account) {
        Club club = new Club();
        String name = ("Espace de " + account.getFirstName() + " " + account.getLastName()).trim();
        club.setName(name);
        club.setSlug(uniqueSlug(account));
        club.setStatus(ClubStatus.ACTIVE);
        club.setSoloPractice(true);
        club.setPersonal(true);
        club = clubRepository.save(club);

        Athlete athlete = new Athlete();
        athlete.setClub(club);
        athlete.setAccount(account);
        athlete.setFirstName(account.getFirstName());
        athlete.setLastName(account.getLastName());
        athlete.setEmail(account.getUser().getEmail());
        athlete.setBirthDate(account.getBirthDate());
        athlete.setSex(account.getSex());
        athlete.setLevel(account.getLevel());
        if (account.getDiscipline() != null) {
            athlete.setDiscipline(account.getDiscipline());
        }
        athlete.setStatus(AthleteStatus.ACTIVE);
        // Le consentement donné à l'inscription couvre aussi ce que l'athlète note lui-même.
        athlete.setHealthDataConsentAt(account.getHealthDataConsentAt());
        athlete = athleteRepository.save(athlete);

        installStarterKit(club.getId());
        log.info("Espace personnel créé pour le compte athlète {}", account.getId());
        return athlete;
    }

    /**
     * Le catalogue de séances de départ, pour que l'athlète ait de quoi s'entraîner seul. Jamais
     * bloquant : un catalogue manquant laisse un espace utilisable.
     */
    private void installStarterKit(UUID clubId) {
        Runnable install = () -> {
            try {
                starterKitService.install(clubId);
            } catch (RuntimeException ex) {
                log.warn("Catalogue de départ non installé pour l'espace personnel {} : {}",
                        clubId, ex.getMessage());
            }
        };
        // Après le commit : le catalogue s'écrit dans sa propre transaction, qui ne verrait pas
        // un espace encore en cours de création.
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

    private String uniqueSlug(AthleteAccount account) {
        // Un préfixe qui ne ressemble à aucun nom de club, et un suffixe aléatoire : le nom d'une
        // personne n'a pas à apparaître dans un identifiant d'URL.
        String base = "perso-" + SlugUtil.slugify(account.getFirstName());
        String slug;
        do {
            slug = base + "-" + UUID.randomUUID().toString().substring(0, 8);
        } while (clubRepository.existsBySlug(slug));
        return slug;
    }
}
