package com.coachrun.controller;

import com.coachrun.exception.NotFoundException;
import com.coachrun.service.CoachShareService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

/**
 * Le lien court d'un coach et ses images de partage.
 *
 * <p>Le front sert {@code /c/<slug>} en relayant vers {@link #preview} : c'est la seule page que
 * les robots de prévisualisation lisent pour un lien de coach. Voir {@link CoachShareService}.</p>
 */
@Tag(name = "Public — Partage des fiches coachs")
@RestController
@RequestMapping("/public/share/coach")
@RequiredArgsConstructor
public class PublicCoachShareController {

    private final CoachShareService share;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    /** La page d'aperçu ; une fiche absente renvoie vers l'annuaire plutôt qu'une erreur brute. */
    @GetMapping(value = "/{slug}", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> preview(@PathVariable String slug) {
        try {
            return ResponseEntity.ok()
                    .cacheControl(CacheControl.maxAge(Duration.ofMinutes(10)).cachePublic())
                    .contentType(new MediaType(MediaType.TEXT_HTML, java.nio.charset.StandardCharsets.UTF_8))
                    .body(share.previewHtml(slug));
        } catch (NotFoundException e) {
            return ResponseEntity.status(HttpStatus.FOUND)
                    .header(HttpHeaders.LOCATION, frontendUrl + "/coachs")
                    .build();
        }
    }

    /** L'image d'aperçu, 1200 × 630. */
    @GetMapping(value = "/{slug}/card.png", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> card(@PathVariable String slug) {
        return image(share.card(slug));
    }

    /** Le format story, 1080 × 1920, que le coach télécharge pour ses réseaux. */
    @GetMapping(value = "/{slug}/story.png", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> story(@PathVariable String slug) {
        return image(share.story(slug));
    }

    private static ResponseEntity<byte[]> image(byte[] png) {
        return ResponseEntity.ok()
                // Une heure : la fiche peut changer, mais un robot qui redemande l'image à chaque
                // affichage ferait redessiner la carte pour rien.
                .cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePublic())
                .contentType(MediaType.IMAGE_PNG)
                .header("X-Content-Type-Options", "nosniff")
                .body(png);
    }
}
