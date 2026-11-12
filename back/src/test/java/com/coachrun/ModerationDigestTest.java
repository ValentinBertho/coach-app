package com.coachrun;

import com.coachrun.entity.CoachProfile;
import com.coachrun.entity.CoachProfileReport;
import com.coachrun.entity.User;
import com.coachrun.entity.enums.CoachProfileStatus;
import com.coachrun.entity.enums.CoachReportReason;
import com.coachrun.entity.enums.CoachReportStatus;
import com.coachrun.entity.enums.UserRole;
import com.coachrun.entity.enums.UserStatus;
import com.coachrun.repository.CoachProfileReportRepository;
import com.coachrun.repository.CoachProfileRepository;
import com.coachrun.repository.NotificationRepository;
import com.coachrun.repository.UserRepository;
import com.coachrun.scheduler.ModerationDigestScheduler;
import com.coachrun.service.ModerationQueueService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Le digest de modération — la fin d'un silence.
 *
 * <p>Les trois files du back-office étaient passives : rien ne disait qu'elles contenaient du
 * travail. Le seul e-mail qui partait sur une demande de club était un accusé de réception au
 * demandeur. Depuis le lot 7, ce silence a un coût : une fiche accusée de porter un faux diplôme
 * reste publiée tant que personne n'ouvre la file.</p>
 *
 * <p>Ce que ces tests fixent : que le message parte quand il y a du travail, qu'il ne parte
 * <b>pas</b> sinon — un digest qui dit « rien » tous les jours cesse d'être ouvert — et qu'il
 * reste <b>un seul message</b> quel que soit le nombre de signalements, ceux-ci étant déposables
 * par n'importe qui, sans compte.</p>
 *
 * <h2>Pourquoi les données sont posées par les dépôts</h2>
 *
 * <p>Et non par des requêtes HTTP. Publier une fiche via l'API laissait un coach publié visible
 * des autres classes de tests, qui faisaient alors échouer leurs propres assertions — c'est ce
 * qu'a révélé `CoachDirectoryTest`, dont le cas « une fiche non publiée est invisible » comptait
 * soudain un coach qui n'était pas le sien. Les entités écrites ici vivent et meurent dans la
 * transaction du test.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ModerationDigestTest {

    @Autowired private ModerationQueueService queueService;
    @Autowired private ModerationDigestScheduler scheduler;
    @Autowired private CoachProfileRepository profileRepository;
    @Autowired private CoachProfileReportRepository reportRepository;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private UserRepository userRepository;

    private CoachProfile profile;
    private User admin;

    @BeforeEach
    void setUp() {
        User coach = userRepository.save(newUser("coach-" + UUID.randomUUID() + "@exemple.fr",
                UserRole.COACH));
        admin = userRepository.save(newUser("admin-" + UUID.randomUUID() + "@exemple.fr",
                UserRole.PLATFORM_ADMIN));

        CoachProfile p = new CoachProfile();
        p.setCoach(coach);
        // DRAFT : la fiche n'est pas publiée, donc elle n'apparaît dans l'annuaire d'aucune autre
        // classe de tests. Un signalement n'a pas besoin d'une fiche publiée pour exister en base.
        p.setStatus(CoachProfileStatus.DRAFT);
        p.setSlug("fiche-test-" + UUID.randomUUID());
        profile = profileRepository.saveAndFlush(p);
    }

    /**
     * Une file vide ne réveille personne.
     *
     * <p>Éprouvé sur le résumé lui-même : les trois files se remplissent par des routes qui
     * écrivent, et vouloir les vider dans un test dirait surtout que le jeu de démonstration n'a
     * rien dedans — pas que la règle tient.</p>
     */
    @Test
    void anEmptyQueueWakesNobody() {
        var empty = new ModerationQueueService.ModerationSummary(0, 0, 0, null);
        assertThat(empty.hasWork())
                .as("un digest qui dit « rien » tous les jours cesse d'être ouvert")
                .isFalse();
        assertThat(empty.total()).isZero();

        assertThat(new ModerationQueueService.ModerationSummary(0, 0, 1, 0L).hasWork())
                .as("un seul signalement suffit à prévenir")
                .isTrue();
    }

    /** Un signalement ouvert entre dans le compte ; le résumé additionne bien les trois files. */
    @Test
    void anOpenReportEntersTheQueue() {
        long before = queueService.summary().openReports();
        openReport("Ce coach affiche un diplôme qu'il n'a pas.");

        var summary = queueService.summary();
        assertThat(summary.openReports() - before).isEqualTo(1);
        assertThat(summary.hasWork()).isTrue();
        assertThat(summary.total()).isEqualTo(summary.pendingClubRequests()
                + summary.pendingCoachProfiles() + summary.openReports());
    }

    /**
     * <b>Un seul message, quel que soit le nombre de signalements.</b>
     *
     * <p>C'est la raison d'être du digest, et elle est de sûreté avant d'être de confort : le
     * signalement est ouvert aux visiteurs sans compte, si bien qu'un e-mail par signalement se
     * déclencherait sur commande. Le plan d'envoi est à cent messages par jour et il porte aussi
     * les réinitialisations de mot de passe — celles qu'on ne peut pas perdre.</p>
     */
    @Test
    void threeReportsStillProduceASingleMessage() {
        for (int i = 0; i < 3; i++) {
            openReport("Propos déplacés dans la présentation, cas " + i);
        }

        long before = digestCount();
        scheduler.sendModerationDigest();

        assertThat(digestCount() - before)
                .as("un administrateur, un message — trois signalements ne font pas trois e-mails")
                .isEqualTo(1);
    }

    /**
     * Un signalement traité sort de la file.
     *
     * <p>Sans quoi le digest redirait chaque matin ce qui a déjà été fait, et l'équipe cesserait de
     * le lire — ce qui reviendrait à ne l'avoir jamais écrit.</p>
     */
    @Test
    void aHandledReportLeavesTheQueue() {
        long before = queueService.summary().openReports();
        CoachProfileReport report = openReport("Publicité pour un site externe.");
        assertThat(queueService.summary().openReports() - before).isEqualTo(1);

        report.handle(CoachReportStatus.DISMISSED, admin.getId(), "Vérifié, rien à corriger.");
        reportRepository.saveAndFlush(report);

        assertThat(queueService.summary().openReports())
                .as("clos, il ne réveille plus personne")
                .isEqualTo(before);
    }

    /**
     * L'âge du plus ancien signalement : le seul chiffre qui distingue une file qui tourne d'une
     * file qui dérape, et la raison pour laquelle il remonte jusque dans l'objet de l'e-mail.
     */
    @Test
    void theSummaryReportsTheAgeOfTheOldestReport() {
        assertThat(new ModerationQueueService.ModerationSummary(0, 0, 0, null).oldestReportAgeDays())
                .as("aucun signalement ouvert : aucun âge, ce qui n'est pas zéro")
                .isNull();

        openReport("Conseils qui relèvent du médecin.");
        assertThat(queueService.summary().oldestReportAgeDays())
                .as("déposé à l'instant : zéro jour")
                .isZero();
    }

    /** Seul OPEN compte : les deux façons de clore sortent également de la file. */
    @Test
    void openIsTheOnlyStatusThatCounts() {
        assertThat(CoachReportStatus.OPEN.isOpen()).isTrue();
        assertThat(CoachReportStatus.ACTED_UPON.isOpen())
                .as("suite donnée : traité, donc hors file")
                .isFalse();
        assertThat(CoachReportStatus.DISMISSED.isOpen())
                .as("sans suite : traité aussi, et la distinction reste au dossier")
                .isFalse();
    }

    // ---------------------------------------------------------------- outillage

    /**
     * Le nombre de notifications de modération déposées pour cet administrateur.
     *
     * <p>C'est l'observable, et non le journal d'e-mails : `MAIL_ENABLED` est à faux dans la suite
     * de tests, si bien qu'aucune ligne d'envoi n'y est jamais écrite. La notification in-app est
     * déposée dans tous les cas — même geste, sur le canal qui ne dépend pas d'un fournisseur.</p>
     */
    private long digestCount() {
        return notificationRepository
                .findByUserIdOrderByCreatedAtDesc(admin.getId(), Pageable.unpaged())
                .stream()
                .filter(n -> "MODERATION_QUEUE".equals(n.getType()))
                .count();
    }

    private CoachProfileReport openReport(String details) {
        CoachProfileReport report = new CoachProfileReport();
        report.setProfile(profile);
        report.setReason(CoachReportReason.OTHER);
        report.setDetails(details);
        report.setStatus(CoachReportStatus.OPEN);
        return reportRepository.saveAndFlush(report);
    }

    private User newUser(String email, UserRole role) {
        User user = new User();
        user.setEmail(email);
        user.setPasswordHash("x");
        user.setFullName("Test");
        user.setRole(role);
        user.setStatus(UserStatus.ACTIVE);
        return user;
    }
}
