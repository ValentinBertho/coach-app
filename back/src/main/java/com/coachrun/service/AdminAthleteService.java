package com.coachrun.service;

import com.coachrun.dto.request.AthleteRequest;
import com.coachrun.dto.response.AdminAthleteResponse;
import com.coachrun.dto.response.AdminInvitationLinkResponse;
import com.coachrun.dto.response.AthleteResponse;
import com.coachrun.dto.response.InvitationAdminResponse;
import com.coachrun.dto.response.PageResponse;
import com.coachrun.entity.Athlete;
import com.coachrun.entity.enums.AdminAuditAction;
import com.coachrun.entity.enums.AdminAuditTarget;
import com.coachrun.entity.enums.AthleteStatus;
import com.coachrun.exception.NotFoundException;
import com.coachrun.repository.AthleteRepository;
import com.coachrun.repository.CoachAthleteRelationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.UUID;

/** Administration des athlètes et invitations (PLATFORM_ADMIN, cross-club). */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminAthleteService {

    /** Même durée que l'invitation posée par un coach : deux règles auraient fini par diverger. */
    private static final int INVITE_VALIDITY_DAYS = 14;

    private static final SecureRandom RANDOM = new SecureRandom();

    private final AthleteRepository athleteRepository;
    private final CoachAthleteRelationRepository relationRepository;
    private final NotificationService notificationService;
    private final AdminAuditService audit;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    public PageResponse<AdminAthleteResponse> list(UUID clubId, AthleteStatus status, String q, Pageable pageable) {
        String query = StringUtils.hasText(q) ? q.trim() : "";
        var page = athleteRepository.searchAdmin(clubId, status, query, pageable);
        // Un comptage groupé pour toute la page : lire la collection de chaque athlète coûterait
        // une requête par ligne, et compterait de surcroît la mauvaise table.
        java.util.Map<UUID, Integer> coachCounts = coachCounts(
                page.getContent().stream().map(Athlete::getId).toList());
        return PageResponse.from(page,
                a -> AdminAthleteResponse.from(a, coachCounts.getOrDefault(a.getId(), 0)));
    }

    private java.util.Map<UUID, Integer> coachCounts(java.util.List<UUID> athleteIds) {
        if (athleteIds.isEmpty()) {
            return java.util.Map.of();
        }
        java.util.Map<UUID, Integer> counts = new java.util.HashMap<>();
        for (Object[] row : relationRepository.countActiveByAthleteIds(athleteIds)) {
            counts.put((UUID) row[0], ((Number) row[1]).intValue());
        }
        return counts;
    }

    public AthleteResponse get(UUID id) {
        return AthleteResponse.from(require(id));
    }

    /**
     * Modification d'un profil athlète depuis l'administration.
     *
     * <p><b>Le résumé d'audit ne porte aucune valeur physiologique.</b> FC max, FC repos, VMA,
     * poids et notes médicales sont des données de santé, chiffrées au repos : les recopier dans
     * le journal les rendrait lisibles en clair, à un endroit qui se conserve longtemps et se
     * consulte largement. On consigne <i>qu'</i>elles ont changé, jamais leur contenu.</p>
     */
    @Transactional
    public AthleteResponse update(UUID id, AthleteRequest request) {
        Athlete a = require(id);
        boolean physiologyChanged = changed(a.getHrMax(), request.hrMax())
                || changed(a.getHrRest(), request.hrRest())
                || changed(a.getVma(), request.vma())
                || changed(a.getWeightKg(), request.weightKg())
                || changed(a.getMedicalNotes(), request.medicalNotes());

        AthleteStatus previousStatus = a.getStatus();

        a.setFirstName(request.firstName());
        a.setLastName(request.lastName());
        a.setEmail(StringUtils.hasText(request.email()) ? request.email().toLowerCase() : null);
        a.setBirthDate(request.birthDate());
        a.setSex(request.sex());
        a.setLevel(request.level());
        a.setHrMax(request.hrMax());
        a.setHrRest(request.hrRest());
        a.setVma(request.vma());
        a.setWeightKg(request.weightKg());
        a.setMedicalNotes(StringUtils.hasText(request.medicalNotes()) ? request.medicalNotes() : null);
        // Nul = inchangé : l'écran d'administration proposait un statut que rien n'appliquait,
        // et aucun autre appelant n'envoie ce champ.
        if (request.status() != null) {
            a.setStatus(request.status());
        }

        String statusChange = request.status() != null && request.status() != previousStatus
                ? " ; statut " + previousStatus + " → " + request.status()
                : "";
        audit.record(AdminAuditAction.ATHLETE_UPDATED, AdminAuditTarget.ATHLETE,
                a.getId(), a.getFirstName() + " " + a.getLastName(),
                (physiologyChanged
                        ? "Profil modifié, dont des données physiologiques (valeurs non consignées)."
                        : "Profil modifié (identité, niveau).") + statusChange);
        return AthleteResponse.from(a);
    }

    @Transactional
    public void delete(UUID id) {
        Athlete a = require(id);
        audit.record(AdminAuditAction.ATHLETE_DELETED, AdminAuditTarget.ATHLETE,
                a.getId(), a.getFirstName() + " " + a.getLastName(),
                "Club " + (a.getClub() != null ? a.getClub().getName() : "—")
                        + " — séances, sorties et ressentis supprimés en cascade.");
        athleteRepository.delete(a);
        log.warn("Athlète supprimé par l'administration (athlete={})", id);
    }

    public PageResponse<InvitationAdminResponse> pendingInvitations(Pageable pageable) {
        return PageResponse.from(athleteRepository.findByInviteTokenIsNotNull(pageable),
                InvitationAdminResponse::from);
    }

    @Transactional
    public void revokeInvitation(UUID athleteId) {
        Athlete a = require(athleteId);
        a.setInviteToken(null);
        a.setInviteExpiresAt(null);
        audit.record(AdminAuditAction.INVITATION_REVOKED, AdminAuditTarget.INVITATION,
                a.getId(), a.getFirstName() + " " + a.getLastName(),
                "Le lien précédent ne fonctionne plus.");
    }

    /**
     * Régénère l'invitation et la renvoie.
     *
     * <p>Seule la révocation existait : une invitation expirée ne laissait d'autre choix que de
     * supprimer l'athlète et de le recréer — ce qui efface son historique — ou de demander à un
     * coach du club de refaire le geste depuis son propre compte.</p>
     *
     * <p>Un nouveau jeton est émis plutôt que l'ancien prolongé : le lien précédent a pu circuler
     * dans une boîte partagée ou une capture d'écran, et repousser sa date de péremption
     * prolongerait aussi cette exposition.</p>
     */
    @Transactional
    public AdminInvitationLinkResponse resendInvitation(UUID athleteId) {
        Athlete a = require(athleteId);
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant expiresAt = Instant.now().plus(INVITE_VALIDITY_DAYS, ChronoUnit.DAYS);
        a.setInviteToken(token);
        a.setInviteExpiresAt(expiresAt);

        String url = frontendUrl + "/invitation/" + token;
        String clubName = a.getClub() != null ? a.getClub().getName() : "votre club";
        boolean emailSent = StringUtils.hasText(a.getEmail());
        if (emailSent) {
            notificationService.notifyAthleteInvitation(a.getEmail(), a.getFirstName(), clubName, url);
        }

        audit.record(AdminAuditAction.INVITATION_RESENT, AdminAuditTarget.INVITATION,
                a.getId(), a.getFirstName() + " " + a.getLastName(),
                "Nouveau lien valable " + INVITE_VALIDITY_DAYS + " jours"
                        + (emailSent ? ", envoyé par e-mail." : ", sans adresse connue (à transmettre à la main)."));
        return new AdminInvitationLinkResponse(url, expiresAt, emailSent);
    }

    private static boolean changed(Object before, Object after) {
        return !java.util.Objects.equals(before, after);
    }

    private Athlete require(UUID id) {
        return athleteRepository.findById(id).orElseThrow(() -> new NotFoundException("Athlète introuvable."));
    }
}
