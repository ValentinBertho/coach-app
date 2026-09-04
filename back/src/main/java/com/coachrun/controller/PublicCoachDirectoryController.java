package com.coachrun.controller;

import com.coachrun.dto.response.CoachFacetsResponse;
import com.coachrun.dto.response.PageResponse;
import com.coachrun.dto.response.PublicCoachDetailResponse;
import com.coachrun.dto.response.PublicCoachSummaryResponse;
import com.coachrun.entity.enums.CoachSpecialty;
import com.coachrun.entity.enums.Discipline;
import com.coachrun.service.CoachDirectoryService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * L'annuaire des coachs, ouvert à qui n'a pas de compte.
 *
 * <h2>Pourquoi c'est public</h2>
 *
 * <p>C'est la vitrine. La mettre derrière une inscription reviendrait à demander à un athlète de
 * créer un compte pour savoir s'il a une raison d'en créer un.</p>
 *
 * <h2>Ce qui en sort, et ce qui n'en sort jamais</h2>
 *
 * <p>Les réponses ne portent ni adresse e-mail, ni téléphone, ni identifiant technique — seulement
 * un slug. Un annuaire est exactement ce qu'on aspire pour se constituer un fichier de démarchage,
 * et la pagination passe par un bucket de plafonnement qui lui est propre
 * ({@code RateLimitFilter.DIRECTORY_BUCKET}).</p>
 *
 * <p>Une fiche non publiée est introuvable ici, et son absence est indistinguable de celle d'une
 * fiche inexistante : répondre différemment apprendrait à un visiteur qu'un coach est inscrit sans
 * l'avoir voulu.</p>
 */
@Tag(name = "Public — Annuaire des coachs")
@RestController
@RequestMapping("/public")
@RequiredArgsConstructor
public class PublicCoachDirectoryController {

    private final CoachDirectoryService directory;

    /**
     * Cherche des coachs. Tous les critères sont facultatifs et se cumulent.
     *
     * <p>{@code remote} vaut « propose le distanciel » quand il est vrai, « propose le présentiel »
     * quand il est faux, et « peu importe » quand il est absent : les deux modes se cumulent chez
     * un même coach, et un booléen qui exclurait l'autre écarterait ceux qui font les deux.</p>
     */
    @GetMapping("/coaches")
    public PageResponse<PublicCoachSummaryResponse> search(
            @RequestParam(required = false) Discipline discipline,
            @RequestParam(required = false) CoachSpecialty specialty,
            @RequestParam(required = false) String language,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) Boolean remote,
            @RequestParam(required = false) Integer maxMonthlyCents,
            @RequestParam(required = false) Boolean acceptingOnly,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size) {
        return directory.search(new CoachDirectoryService.DirectoryQuery(
                discipline, specialty, language, city, remote, maxMonthlyCents, acceptingOnly),
                Math.max(0, page), Math.min(Math.max(1, size), 48));
    }

    /**
     * Le repli d'une recherche sans résultat : les coachs qui prennent des athlètes.
     *
     * <p>Une route à part, appelée sciemment par l'écran. L'ouverture est prévue à dix coachs ;
     * croisée avec discipline, langue, ville et distanciel, une recherche rendra souvent zéro, et
     * une liste vide fait conclure au visiteur que la plateforme est vide plutôt que son filtre
     * trop étroit. Le repli n'est pas glissé dans les résultats : ce serait faire croire que le
     * filtre a marché.</p>
     */
    // Hors de /coaches/{slug} à dessein : Spring donne bien la priorité au segment littéral, mais
    // la fiche d'un coach dont le slug serait « suggestions » deviendrait alors inatteignable.
    // Une adresse distincte coûte moins qu'un piège qui ne se révélerait qu'une fois.
    @GetMapping("/coach-suggestions")
    public PageResponse<PublicCoachSummaryResponse> suggestions(
            @RequestParam(defaultValue = "12") int size) {
        return directory.fallback(Math.min(Math.max(1, size), 48));
    }

    /** Les valeurs de filtre et leur nombre de coachs, pour ne jamais proposer un filtre vide. */
    @GetMapping("/coach-facets")
    public CoachFacetsResponse facets() {
        return directory.facets();
    }

    /** Une fiche publique, par son adresse lisible. */
    @GetMapping("/coaches/{slug}")
    public PublicCoachDetailResponse bySlug(@PathVariable String slug) {
        return directory.bySlug(slug);
    }
}
