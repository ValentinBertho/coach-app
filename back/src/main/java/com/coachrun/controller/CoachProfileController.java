package com.coachrun.controller;

import com.coachrun.dto.request.CoachCertificationRequest;
import com.coachrun.dto.request.CoachOfferRequest;
import com.coachrun.dto.request.CoachProfileRequest;
import com.coachrun.dto.response.CoachCertificationResponse;
import com.coachrun.dto.response.CoachOfferResponse;
import com.coachrun.dto.response.CoachProfileResponse;
import com.coachrun.entity.enums.AthleteLevel;
import com.coachrun.entity.enums.CoachSpecialty;
import com.coachrun.entity.enums.Discipline;
import com.coachrun.entity.enums.OfferPeriodicity;
import com.coachrun.security.AuthPrincipal;
import com.coachrun.service.CoachProfileService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * La fiche publique du coach, vue par son propriétaire.
 *
 * <p>Sous {@code /me} et non {@code /clubs/{clubId}/…} : cette fiche appartient à une <b>personne</b>,
 * pas à un club. Un coach qui intervient dans deux clubs n'a qu'une vitrine, et un indépendant n'a
 * pas de club à mettre dans l'adresse.</p>
 */
@Tag(name = "Coach — Fiche publique")
@RestController
@RequestMapping("/me/coach-profile")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('COACH','HEAD_COACH')")
public class CoachProfileController {

    private final CoachProfileService service;

    /** La fiche du coach connecté ; créée en brouillon si elle n'existe pas encore. */
    @GetMapping
    public CoachProfileResponse get(@AuthenticationPrincipal AuthPrincipal principal) {
        return service.myProfile(principal.userId());
    }

    @PutMapping
    public CoachProfileResponse update(@AuthenticationPrincipal AuthPrincipal principal,
                                       @Valid @RequestBody CoachProfileRequest request) {
        return service.update(principal.userId(), request);
    }

    /** Soumet la fiche à validation. Refusé, avec la liste des manques, si elle est incomplète. */
    @PostMapping("/submit")
    public CoachProfileResponse submit(@AuthenticationPrincipal AuthPrincipal principal) {
        return service.submit(principal.userId());
    }

    /** Le coach ouvre ou ferme sa fiche aux nouvelles demandes, sans la retirer de l'annuaire. */
    @PostMapping("/accepting")
    public CoachProfileResponse setAccepting(@AuthenticationPrincipal AuthPrincipal principal,
                                             @RequestParam boolean accepting) {
        return service.setAcceptingAthletes(principal.userId(), accepting);
    }

    // --- Photo ---

    /**
     * Remplace la photo de la fiche.
     *
     * <p>Le serveur ne conserve jamais le fichier reçu : il le décode, le réduit et le ré-encode
     * en JPEG. Les métadonnées EXIF — coordonnées GPS du domicile comprises — disparaissent au
     * passage, ce qui n'est pas un détail pour une image destinée à un annuaire public.</p>
     */
    @PostMapping(value = "/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public CoachProfileResponse uploadPhoto(@AuthenticationPrincipal AuthPrincipal principal,
                                            @RequestParam("file") MultipartFile file) {
        return service.replacePhoto(principal.userId(), file);
    }

    @DeleteMapping("/photo")
    public CoachProfileResponse deletePhoto(@AuthenticationPrincipal AuthPrincipal principal) {
        return service.deletePhoto(principal.userId());
    }

    // --- Certifications : déclaratives, jamais certifiées par la plateforme ---

    @PostMapping("/certifications")
    @ResponseStatus(HttpStatus.CREATED)
    public CoachCertificationResponse addCertification(
            @AuthenticationPrincipal AuthPrincipal principal,
            @Valid @RequestBody CoachCertificationRequest request) {
        return service.addCertification(principal.userId(), request);
    }

    @DeleteMapping("/certifications/{certificationId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteCertification(@AuthenticationPrincipal AuthPrincipal principal,
                                    @PathVariable UUID certificationId) {
        service.deleteCertification(principal.userId(), certificationId);
    }

    // --- Formules ---

    @PostMapping("/offers")
    @ResponseStatus(HttpStatus.CREATED)
    public CoachOfferResponse addOffer(@AuthenticationPrincipal AuthPrincipal principal,
                                       @Valid @RequestBody CoachOfferRequest request) {
        return service.addOffer(principal.userId(), request);
    }

    @PutMapping("/offers/{offerId}")
    public CoachOfferResponse updateOffer(@AuthenticationPrincipal AuthPrincipal principal,
                                          @PathVariable UUID offerId,
                                          @Valid @RequestBody CoachOfferRequest request) {
        return service.updateOffer(principal.userId(), offerId, request);
    }

    /** Retire la formule de la fiche sans la supprimer : un accord passé garde son libellé. */
    @DeleteMapping("/offers/{offerId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deactivateOffer(@AuthenticationPrincipal AuthPrincipal principal,
                                @PathVariable UUID offerId) {
        service.deactivateOffer(principal.userId(), offerId);
    }

    /**
     * Le vocabulaire de la fiche : disciplines, spécialités, niveaux, périodicités.
     *
     * <p>Servi plutôt que codé en dur côté front : ce sont les mêmes valeurs qui deviendront les
     * facettes de l'annuaire, et deux listes qui divergent produisent un filtre qui ne rend rien.</p>
     */
    @GetMapping("/vocabulary")
    public Map<String, List<Map<String, String>>> vocabulary() {
        return Map.of(
                "disciplines", List.of(Discipline.values()).stream()
                        .map(d -> Map.of("value", d.name(), "label", d.name().charAt(0)
                                + d.name().substring(1).toLowerCase(java.util.Locale.ROOT))).toList(),
                "specialties", CoachSpecialty.all().stream()
                        .map(s -> Map.of("value", s.name(), "label", s.label())).toList(),
                "levels", List.of(AthleteLevel.values()).stream()
                        .map(l -> Map.of("value", l.name(), "label", levelLabel(l))).toList(),
                "periodicities", List.of(OfferPeriodicity.values()).stream()
                        .map(p -> Map.of("value", p.name(), "label", p.label())).toList());
    }

    private static String levelLabel(AthleteLevel level) {
        return switch (level) {
            case BEGINNER -> "Débutant";
            case INTERMEDIATE -> "Intermédiaire";
            case ADVANCED -> "Confirmé";
            case ELITE -> "Élite";
        };
    }
}
