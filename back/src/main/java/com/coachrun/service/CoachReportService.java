package com.coachrun.service;

import com.coachrun.dto.request.CoachReportSubmission;
import com.coachrun.dto.response.CoachReportResponse;
import com.coachrun.entity.CoachProfile;
import com.coachrun.entity.CoachProfileReport;
import com.coachrun.entity.enums.CoachReportStatus;
import com.coachrun.exception.ConflictException;
import com.coachrun.exception.NotFoundException;
import com.coachrun.repository.CoachProfileReportRepository;
import com.coachrun.repository.CoachProfileRepository;
import com.coachrun.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

/**
 * Le signalement d'une fiche coach, et son arbitrage.
 *
 * <h2>Pourquoi ce service existe</h2>
 *
 * <p>La décision 4 affiche les diplômes comme <b>déclarés par le coach</b>, sans vérification. La
 * plateforme a donc renoncé à garantir ; il lui reste l'obligation d'écouter. Publier des
 * affirmations invérifiées sans offrir de les contester donnerait l'autorité de la publication
 * sans le recours qui la rend supportable.</p>
 *
 * <h2>Ce que le service ne fait pas</h2>
 *
 * <p><b>Rien d'automatique.</b> Aucun seuil de signalements ne dépublie une fiche. Un dispositif
 * qui suspend au troisième signalement se retourne le jour où trois personnes s'accordent pour
 * nuire à un concurrent, et le coach n'a alors ni prévenu ni recours. La suspension reste un geste
 * d'administrateur, déjà outillé ({@code CoachProfileService.suspend}).</p>
 *
 * <p><b>Rien n'est dit au coach.</b> Le signalé n'est pas notifié : il identifierait sans peine qui
 * l'a signalé — souvent l'un de ses trois athlètes — avant qu'un humain ait seulement établi si le
 * reproche tenait. C'est l'arbitrage qui le contacte, s'il y a lieu de le contacter.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CoachReportService {

    /**
     * Au-delà, cette adresse a dit ce qu'elle avait à dire sur cette fiche.
     *
     * <p>Trois et pas un : le premier signalement peut être maladroit, le deuxième préciser. Le
     * onzième est de l'acharnement, et il noierait la file sous un seul dossier.</p>
     */
    static final int MAX_PER_PROFILE_PER_IP = 3;

    /** Borne de volume par adresse et par jour, tous coachs confondus. */
    static final int MAX_PER_DAY_PER_IP = 10;

    private final CoachProfileReportRepository reportRepository;
    private final CoachProfileRepository profileRepository;
    private final UserRepository userRepository;

    /**
     * Dépose un signalement sur une fiche publiée.
     *
     * @param reporterUserId l'auteur s'il est connecté, {@code null} sinon — le signalement anonyme
     *                       est accepté à dessein (cf. {@link CoachProfileReport})
     */
    @Transactional
    public void submit(String slug, CoachReportSubmission submission,
                       UUID reporterUserId, String ip, String userAgent) {
        CoachProfile profile = profileRepository.findBySlug(slug)
                .filter(p -> p.getStatus().isVisible())
                // Même formulation que l'annuaire : une fiche non publiée reste indistinguable
                // d'une fiche inexistante, y compris ici.
                .orElseThrow(() -> new NotFoundException("Cette fiche n'existe pas ou n'est plus publiée."));

        String clientIp = truncate(ip, 64);
        if (clientIp != null) {
            if (reportRepository.countByProfileIdAndIpAddress(profile.getId(), clientIp)
                    >= MAX_PER_PROFILE_PER_IP) {
                throw new ConflictException(
                        "Vous avez déjà signalé cette fiche. Elle est dans la file de modération : "
                                + "un nouveau signalement ne la fera pas traiter plus vite.");
            }
            if (reportRepository.countByIpAddressAndCreatedAtAfter(
                    clientIp, Instant.now().minus(1, ChronoUnit.DAYS)) >= MAX_PER_DAY_PER_IP) {
                throw new ConflictException("Trop de signalements envoyés aujourd'hui. Réessayez demain.");
            }
        }

        CoachProfileReport report = new CoachProfileReport();
        report.setProfile(profile);
        report.setReason(submission.reason());
        report.setDetails(submission.details().trim());
        report.setStatus(CoachReportStatus.OPEN);
        report.setIpAddress(clientIp);
        report.setUserAgent(truncate(userAgent, 512));
        if (reporterUserId != null) {
            report.setReporter(userRepository.findById(reporterUserId).orElse(null));
        }
        reportRepository.save(report);

        // Le motif et le nombre, jamais le texte : les détails d'un signalement contiennent des
        // affirmations sur une personne nommée, et un journal d'application n'est pas l'endroit
        // où elles doivent vivre.
        log.warn("Fiche coach {} signalée ({}) — {} signalement(s) ouvert(s)",
                profile.getSlug(), report.getReason(),
                reportRepository.countByProfileIdAndStatus(profile.getId(), CoachReportStatus.OPEN));
    }

    /** La file d'arbitrage, du plus ancien au plus récent. */
    @Transactional(readOnly = true)
    public List<CoachReportResponse> queue(CoachReportStatus status) {
        return reportRepository.findByStatusOrderByCreatedAtAsc(
                        status == null ? CoachReportStatus.OPEN : status)
                .stream()
                .map(r -> CoachReportResponse.from(r,
                        reportRepository.countByProfileIdAndStatus(
                                r.getProfile().getId(), CoachReportStatus.OPEN)))
                .toList();
    }

    /**
     * Clôt un signalement, avec ou sans suite.
     *
     * <p>Clore ne touche pas à la fiche : suspendre reste un geste distinct et explicite. Enchaîner
     * les deux ici ferait d'un clic de tri une sanction, ce qui est exactement l'automatisme qu'on
     * refuse.</p>
     */
    @Transactional
    public CoachReportResponse handle(UUID reportId, CoachReportStatus outcome,
                                      UUID adminUserId, String note) {
        if (outcome == null || outcome.isOpen()) {
            throw new ConflictException("Un signalement se clôt avec suite ou sans suite.");
        }
        CoachProfileReport report = reportRepository.findById(reportId)
                .orElseThrow(() -> new NotFoundException("Ce signalement n'existe pas."));
        report.handle(outcome, adminUserId, truncate(note, 2000));
        log.info("Signalement {} clos ({}) par {}", reportId, outcome, adminUserId);
        return CoachReportResponse.from(report,
                reportRepository.countByProfileIdAndStatus(
                        report.getProfile().getId(), CoachReportStatus.OPEN));
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
