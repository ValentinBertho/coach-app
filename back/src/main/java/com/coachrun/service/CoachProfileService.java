package com.coachrun.service;

import com.coachrun.dto.request.CoachCertificationRequest;
import com.coachrun.dto.request.CoachOfferRequest;
import com.coachrun.dto.request.CoachProfileRequest;
import com.coachrun.dto.response.AdminCoachProfileResponse;
import com.coachrun.dto.response.CoachCertificationResponse;
import com.coachrun.dto.response.CoachOfferResponse;
import com.coachrun.dto.response.CoachProfileResponse;
import com.coachrun.dto.response.PageResponse;
import com.coachrun.entity.CoachCertification;
import com.coachrun.entity.CoachOffer;
import com.coachrun.entity.CoachProfile;
import com.coachrun.entity.User;
import com.coachrun.entity.enums.CoachProfileStatus;
import com.coachrun.entity.enums.UserRole;
import com.coachrun.exception.ApiException;
import com.coachrun.exception.ConflictException;
import com.coachrun.exception.NotFoundException;
import com.coachrun.repository.CoachCertificationRepository;
import com.coachrun.repository.CoachOfferRepository;
import com.coachrun.repository.CoachPhotoRepository;
import com.coachrun.repository.CoachProfileRepository;
import com.coachrun.repository.UserRepository;
import com.coachrun.security.AuthPrincipal;
import com.coachrun.util.SlugUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * La vitrine d'un coach : son écriture, sa soumission, son arbitrage.
 *
 * <h2>Le cycle, et pourquoi il est ce qu'il est</h2>
 *
 * <pre>
 *   DRAFT ──soumission──▶ PENDING ──validation──▶ PUBLISHED ⇄ CLOSED
 *                            │                       │
 *                            └──refus──▶ DRAFT       └──suspension──▶ SUSPENDED
 * </pre>
 *
 * <p><b>La validation est manuelle</b>, et elle le reste pendant la bêta : une place de marché se
 * juge à son pire profil, et rien n'est automatisable avant d'avoir vu cent dossiers. Le patron est
 * celui des demandes de création de club, qui fonctionne déjà.</p>
 *
 * <p><b>Une fiche publiée se modifie sans repasser par la file.</b> C'est un arbitrage, et il se
 * discute : renvoyer chaque correction en validation serait plus sûr, mais retirerait de l'annuaire
 * un coach qui corrige une faute de frappe, parfois pour deux jours. La porte d'entrée est le
 * garde-fou ; la suspension est le recours. Si l'usage montre des fiches qui dérivent après
 * publication, c'est ici qu'il faudra resserrer.</p>
 *
 * <p><b>Ce service n'accorde aucun droit.</b> Publier une fiche ne donne accès à rien ; suspendre
 * une fiche ne retire à personne ses athlètes. La vitrine et le travail sont deux choses
 * séparées.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CoachProfileService {

    private final CoachProfileRepository profileRepository;
    private final CoachCertificationRepository certificationRepository;
    private final CoachOfferRepository offerRepository;
    private final CoachPhotoRepository photoRepository;
    private final CoachPhotoService photoService;
    private final UserRepository userRepository;

    // ---------------------------------------------------------------- côté coach

    /**
     * La fiche du coach, créée en brouillon à la première lecture.
     *
     * <p>Provisionnement paresseux, comme les zones d'intensité : demander au coach de « créer sa
     * fiche » avant de pouvoir la remplir est une étape qui n'apprend rien à personne. Il ouvre
     * l'écran, il écrit.</p>
     */
    @Transactional
    public CoachProfileResponse myProfile(UUID coachId) {
        return render(getOrCreate(coachId));
    }

    @Transactional
    public CoachProfileResponse update(UUID coachId, CoachProfileRequest request) {
        CoachProfile profile = requireEditable(coachId);
        profile.setHeadline(trimToNull(request.headline()));
        profile.setBio(trimToNull(request.bio()));
        profile.setDisciplines(nonNull(request.disciplines()));
        profile.setSpecialties(nonNull(request.specialties()));
        profile.setLevels(nonNull(request.levels()));
        profile.setLanguages(normalizedLanguages(request.languages()));
        profile.setCity(trimToNull(request.city()));
        profile.setCountry(request.country() == null ? null
                : request.country().trim().toUpperCase(Locale.ROOT));
        profile.setRemote(request.remote());
        profile.setInPerson(request.inPerson());
        profile.setExperienceYears(request.experienceYears());
        profile.setCapacityMax(request.capacityMax());
        // Une fiche refusée qu'on retravaille repart propre : garder le motif l'aurait affiché
        // au-dessus d'un texte qui ne lui correspond plus.
        if (profile.getStatus() == CoachProfileStatus.DRAFT) {
            profile.setReviewNote(null);
        }
        return render(profile);
    }

    /**
     * Soumet la fiche à la validation.
     *
     * <p>Le contrôle de complétude est ici et pas sur le DTO : il porte sur l'ensemble — une fiche,
     * ses formules, ses spécialités — et il ne s'applique qu'à ce geste. Le message nomme chaque
     * manque, parce qu'un « fiche incomplète » oblige le coach à deviner ce qu'on attend.</p>
     */
    @Transactional
    public CoachProfileResponse submit(UUID coachId) {
        CoachProfile profile = getOrCreate(coachId);
        if (profile.getStatus() == CoachProfileStatus.PENDING) {
            throw new ConflictException("Votre fiche est déjà en cours de validation.");
        }
        if (profile.getStatus() == CoachProfileStatus.SUSPENDED) {
            throw new ConflictException(
                    "Votre fiche a été suspendue par l'équipe. Répondez à l'e-mail reçu pour "
                            + "en discuter : elle ne peut pas être resoumise depuis cet écran.");
        }
        List<String> missing = missing(profile);
        if (!missing.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "Il manque encore : " + String.join(", ", missing) + ".");
        }
        profile.setStatus(CoachProfileStatus.PENDING);
        profile.setSubmittedAt(Instant.now());
        profile.setReviewNote(null);
        log.info("Fiche coach {} soumise à validation", profile.getId());
        return render(profile);
    }

    /**
     * Le coach ferme ou rouvre sa fiche aux nouvelles demandes.
     *
     * <p>Fermée, elle reste consultable : un athlète qui la cherche doit pouvoir constater qu'elle
     * existe plutôt que de conclure à une disparition. Elle n'accepte simplement plus de demande.
     * C'est une décision du coach, distincte d'une suspension, qui est une décision de la
     * plateforme — les confondre ferait porter une sanction à qui prend seulement une pause.</p>
     */
    @Transactional
    public CoachProfileResponse setAcceptingAthletes(UUID coachId, boolean accepting) {
        CoachProfile profile = getOrCreate(coachId);
        if (profile.getStatus() != CoachProfileStatus.PUBLISHED
                && profile.getStatus() != CoachProfileStatus.CLOSED) {
            throw new ConflictException(
                    "Cette bascule ne concerne qu'une fiche publiée.");
        }
        profile.setStatus(accepting ? CoachProfileStatus.PUBLISHED : CoachProfileStatus.CLOSED);
        return render(profile);
    }

    // ---------------------------------------------------------------- photo

    /**
     * Remplace la photo de la fiche.
     *
     * <p>Passe par {@code requireEditable} comme le reste : une fiche en cours de validation est
     * gelée, photo comprise — sinon l'administrateur validerait un visage et en publierait un
     * autre.</p>
     */
    @Transactional
    public CoachProfileResponse replacePhoto(UUID coachId,
                                             org.springframework.web.multipart.MultipartFile file) {
        CoachProfile profile = requireEditable(coachId);
        photoService.replace(profile, file);
        return render(profile);
    }

    @Transactional
    public CoachProfileResponse deletePhoto(UUID coachId) {
        CoachProfile profile = requireEditable(coachId);
        photoService.delete(profile);
        return render(profile);
    }

    // ---------------------------------------------------------------- certifications

    @Transactional
    public CoachCertificationResponse addCertification(UUID coachId, CoachCertificationRequest r) {
        CoachProfile profile = requireEditable(coachId);
        CoachCertification c = new CoachCertification();
        c.setProfile(profile);
        c.setLabel(r.label().trim());
        c.setOrganisation(trimToNull(r.organisation()));
        c.setObtainedYear(r.obtainedYear());
        return CoachCertificationResponse.from(certificationRepository.save(c));
    }

    @Transactional
    public void deleteCertification(UUID coachId, UUID certificationId) {
        CoachProfile profile = requireEditable(coachId);
        CoachCertification c = certificationRepository
                .findByIdAndProfileId(certificationId, profile.getId())
                .orElseThrow(() -> new NotFoundException("Certification introuvable."));
        certificationRepository.delete(c);
    }

    // ---------------------------------------------------------------- formules

    @Transactional
    public CoachOfferResponse addOffer(UUID coachId, CoachOfferRequest r) {
        CoachProfile profile = requireEditable(coachId);
        CoachOffer o = new CoachOffer();
        o.setProfile(profile);
        apply(o, r);
        return CoachOfferResponse.from(offerRepository.save(o));
    }

    @Transactional
    public CoachOfferResponse updateOffer(UUID coachId, UUID offerId, CoachOfferRequest r) {
        CoachProfile profile = requireEditable(coachId);
        CoachOffer o = offerRepository.findByIdAndProfileId(offerId, profile.getId())
                .orElseThrow(() -> new NotFoundException("Formule introuvable."));
        apply(o, r);
        return CoachOfferResponse.from(o);
    }

    /**
     * Retire une formule de la fiche.
     *
     * <p>Désactivation, pas suppression : cette formule est peut-être celle sur laquelle un athlète
     * a été accepté, et son libellé doit rester lisible dans l'historique de l'accord. La demande
     * de coaching en gardera de toute façon un instantané, mais deux protections valent mieux
     * qu'une quand il s'agit du prix convenu entre deux personnes.</p>
     */
    @Transactional
    public void deactivateOffer(UUID coachId, UUID offerId) {
        CoachProfile profile = requireEditable(coachId);
        CoachOffer o = offerRepository.findByIdAndProfileId(offerId, profile.getId())
                .orElseThrow(() -> new NotFoundException("Formule introuvable."));
        o.setActive(false);
    }

    private void apply(CoachOffer o, CoachOfferRequest r) {
        o.setName(r.name().trim());
        o.setDescription(trimToNull(r.description()));
        o.setAmountCents(r.amountCents());
        o.setPeriodicity(r.periodicity());
        o.setActive(r.active());
        o.setPosition(r.position());
    }

    // ---------------------------------------------------------------- côté administration

    public PageResponse<AdminCoachProfileResponse> list(CoachProfileStatus status, Pageable pageable) {
        var page = status == null
                ? profileRepository.findAllByOrderByUpdatedAtDesc(pageable)
                : profileRepository.findByStatusOrderBySubmittedAtAsc(status, pageable);
        return PageResponse.from(page, this::renderForAdmin);
    }

    public long countPending() {
        return profileRepository.countByStatus(CoachProfileStatus.PENDING);
    }

    /** Publie la fiche. Le slug est posé ici s'il ne l'était pas, et jamais réécrit ensuite. */
    @Transactional
    public AdminCoachProfileResponse approve(UUID profileId, String note, AuthPrincipal actor) {
        CoachProfile profile = require(profileId);
        if (profile.getStatus() != CoachProfileStatus.PENDING) {
            throw new ConflictException(
                    "Seule une fiche en attente de validation peut être publiée (celle-ci est « "
                            + profile.getStatus().label() + " »).");
        }
        profile.setStatus(CoachProfileStatus.PUBLISHED);
        profile.setPublishedAt(Instant.now());
        stampReview(profile, note, actor);
        log.info("Fiche coach {} publiée", profileId);
        return renderForAdmin(profile);
    }

    /**
     * Refuse la fiche : elle repasse en brouillon, avec son motif.
     *
     * <p>Repasser en brouillon plutôt que d'inventer un état « refusée » : ce que le coach doit
     * faire, c'est la reprendre. Un état terminal l'aurait laissé devant une porte close, sans
     * savoir qu'il pouvait corriger et resoumettre.</p>
     */
    @Transactional
    public AdminCoachProfileResponse reject(UUID profileId, String note, AuthPrincipal actor) {
        CoachProfile profile = require(profileId);
        if (profile.getStatus() != CoachProfileStatus.PENDING) {
            throw new ConflictException("Seule une fiche en attente de validation peut être refusée.");
        }
        profile.setStatus(CoachProfileStatus.DRAFT);
        profile.setSubmittedAt(null);
        stampReview(profile, note, actor);
        log.info("Fiche coach {} refusée", profileId);
        return renderForAdmin(profile);
    }

    /** Retire une fiche de l'annuaire. Ne touche ni au compte du coach, ni à ses athlètes. */
    @Transactional
    public AdminCoachProfileResponse suspend(UUID profileId, String note, AuthPrincipal actor) {
        CoachProfile profile = require(profileId);
        profile.setStatus(CoachProfileStatus.SUSPENDED);
        stampReview(profile, note, actor);
        log.info("Fiche coach {} suspendue", profileId);
        return renderForAdmin(profile);
    }

    /** Lève une suspension : la fiche retourne en validation, pas directement dans l'annuaire. */
    @Transactional
    public AdminCoachProfileResponse reinstate(UUID profileId, String note, AuthPrincipal actor) {
        CoachProfile profile = require(profileId);
        if (profile.getStatus() != CoachProfileStatus.SUSPENDED) {
            throw new ConflictException("Cette fiche n'est pas suspendue.");
        }
        profile.setStatus(CoachProfileStatus.PENDING);
        profile.setSubmittedAt(Instant.now());
        stampReview(profile, note, actor);
        return renderForAdmin(profile);
    }

    private void stampReview(CoachProfile profile, String note, AuthPrincipal actor) {
        profile.setReviewedAt(Instant.now());
        profile.setReviewedByUserId(actor == null ? null : actor.userId());
        profile.setReviewNote(trimToNull(note));
    }

    // ---------------------------------------------------------------- interne

    private CoachProfile getOrCreate(UUID coachId) {
        return profileRepository.findByCoachId(coachId).orElseGet(() -> {
            User coach = userRepository.findById(coachId)
                    .orElseThrow(() -> new NotFoundException("Compte introuvable."));
            if (coach.getRole() != UserRole.COACH && coach.getRole() != UserRole.HEAD_COACH) {
                throw new ConflictException("Seul un coach peut avoir une fiche publique.");
            }
            CoachProfile profile = new CoachProfile();
            profile.setCoach(coach);
            profile.setStatus(CoachProfileStatus.DRAFT);
            profile.setSlug(uniqueSlug(coach.getFullName()));
            profile.setRemote(true);
            return profileRepository.save(profile);
        });
    }

    /**
     * La fiche, si elle est modifiable.
     *
     * <p>Une fiche en attente d'arbitrage est gelée : sans cela, l'administrateur validerait un
     * texte que le coach a changé entre-temps, et la validation ne voudrait plus rien dire.</p>
     */
    private CoachProfile requireEditable(UUID coachId) {
        CoachProfile profile = getOrCreate(coachId);
        if (profile.getStatus() == CoachProfileStatus.PENDING) {
            throw new ConflictException(
                    "Votre fiche est en cours de validation : elle ne peut pas être modifiée tant "
                            + "que l'équipe ne s'est pas prononcée.");
        }
        return profile;
    }

    private CoachProfile require(UUID profileId) {
        return profileRepository.findById(profileId)
                .orElseThrow(() -> new NotFoundException("Fiche introuvable."));
    }

    /**
     * Ce qui manque pour soumettre — nommé, pas compté.
     *
     * <p>Une formule est exigée : la décision produit est d'afficher les tarifs dès l'ouverture, et
     * c'est un critère de recherche de l'annuaire. Une fiche sans tarif y serait invisible au
     * premier filtre, ce qui est pire pour le coach que de ne pas être publié.</p>
     */
    private List<String> missing(CoachProfile p) {
        List<String> missing = new ArrayList<>();
        if (!StringUtils.hasText(p.getHeadline())) {
            missing.add("une accroche");
        }
        if (!StringUtils.hasText(p.getBio()) || p.getBio().trim().length() < 120) {
            missing.add("une présentation d'au moins 120 caractères");
        }
        if (p.getDisciplines().isEmpty()) {
            missing.add("au moins une discipline");
        }
        if (p.getSpecialties().isEmpty()) {
            missing.add("au moins une spécialité");
        }
        if (p.getLanguages().isEmpty()) {
            missing.add("au moins une langue");
        }
        if (!p.isRemote() && !p.isInPerson()) {
            missing.add("à distance ou en présentiel (au moins l'un des deux)");
        }
        if (p.isInPerson() && !StringUtils.hasText(p.getCity())) {
            missing.add("une ville (vous proposez du présentiel)");
        }
        if (!offerRepository.existsByProfileIdAndActiveTrue(p.getId())) {
            missing.add("au moins une formule tarifaire");
        }
        return missing;
    }

    private CoachProfileResponse render(CoachProfile p) {
        return CoachProfileResponse.of(p, photoUrl(p), certifications(p), offers(p), missing(p));
    }

    /**
     * L'adresse publique de la photo, ou {@code null}.
     *
     * <p>Seul l'identifiant est lu, jamais les octets : cette méthode est appelée à chaque lecture
     * de fiche, et l'annuaire en lira vingt par page.</p>
     */
    private String photoUrl(CoachProfile p) {
        return photoRepository.findIdByProfileId(p.getId())
                .map(id -> "/public/coach-photos/" + id)
                .orElse(null);
    }

    private AdminCoachProfileResponse renderForAdmin(CoachProfile p) {
        String reviewedBy = p.getReviewedByUserId() == null ? null
                : userRepository.findById(p.getReviewedByUserId()).map(User::getEmail).orElse(null);
        return AdminCoachProfileResponse.of(p, reviewedBy, photoUrl(p), certifications(p), offers(p));
    }

    private List<CoachCertificationResponse> certifications(CoachProfile p) {
        return certificationRepository.findByProfileIdOrderByObtainedYearDesc(p.getId())
                .stream().map(CoachCertificationResponse::from).toList();
    }

    private List<CoachOfferResponse> offers(CoachProfile p) {
        return offerRepository.findByProfileIdOrderByPositionAscCreatedAtAsc(p.getId())
                .stream().map(CoachOfferResponse::from).toList();
    }

    private String uniqueSlug(String fullName) {
        String base = SlugUtil.slugify(fullName);
        String slug = base;
        int i = 1;
        while (profileRepository.existsBySlug(slug)) {
            slug = base + "-" + (++i);
        }
        return slug;
    }

    private static String trimToNull(String s) {
        return StringUtils.hasText(s) ? s.trim() : null;
    }

    private static <T> Set<T> nonNull(Set<T> in) {
        return in == null ? new java.util.HashSet<>() : new java.util.HashSet<>(in);
    }

    /** Codes de langue normalisés en minuscules : « FR » et « fr » sont la même langue. */
    private static Set<String> normalizedLanguages(Set<String> in) {
        if (in == null) {
            return new java.util.HashSet<>();
        }
        return in.stream()
                .filter(StringUtils::hasText)
                .map(l -> l.trim().toLowerCase(Locale.ROOT))
                .collect(java.util.stream.Collectors.toCollection(java.util.HashSet::new));
    }
}
