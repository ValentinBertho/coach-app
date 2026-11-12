package com.coachrun.controller;

import com.coachrun.entity.CoachPhoto;
import com.coachrun.service.CoachPhotoService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.UUID;

/**
 * Les photos des fiches coachs, servies sans authentification.
 *
 * <h2>Pourquoi c'est public</h2>
 *
 * <p>L'annuaire l'est : une vitrine derrière un mot de passe ne sert à rien. Ces images sont
 * destinées à être vues par des visiteurs qui n'ont pas de compte, et c'est leur seule raison
 * d'exister.</p>
 *
 * <h2>Ce qui protège une photo qui n'est pas encore publiée</h2>
 *
 * <p>L'adresse contient l'identifiant de la photo, un UUID que rien n'expose avant la publication :
 * il n'est rendu qu'au coach, dans sa propre fiche. Une photo de brouillon n'est donc atteignable
 * que par quelqu'un à qui son propriétaire a donné le lien. C'est le même raisonnement qu'un lien
 * de partage non listé, et c'est un choix — pas un oubli : filtrer sur le statut de la fiche
 * rendrait l'aperçu impossible dans l'éditeur, là où le coach en a précisément besoin.</p>
 *
 * <p>L'identifiant change à chaque remplacement, ce qui autorise un cache long : l'ancienne adresse
 * ne désigne plus rien, la nouvelle est neuve, et rien n'a à être invalidé.</p>
 */
@Tag(name = "Public — Photos de fiches coachs")
@RestController
@RequestMapping("/public/coach-photos")
@RequiredArgsConstructor
public class PublicCoachPhotoController {

    private final CoachPhotoService photoService;

    @GetMapping("/{photoId}")
    public ResponseEntity<byte[]> photo(@PathVariable UUID photoId) {
        CoachPhoto photo = photoService.require(photoId);
        return ResponseEntity.ok()
                // L'adresse porte l'identifiant, et il change à chaque remplacement : le contenu
                // derrière une adresse donnée ne bouge jamais.
                .cacheControl(CacheControl.maxAge(Duration.ofDays(30)).cachePublic().immutable())
                .contentType(MediaType.IMAGE_JPEG)
                // Le type est imposé, jamais repris de l'envoi : le serveur a lui-même produit ce
                // JPEG, et laisser un client décider du type d'une réponse d'image est le début
                // d'un XSS par fichier téléversé.
                .header("X-Content-Type-Options", "nosniff")
                .body(photo.getData());
    }
}
