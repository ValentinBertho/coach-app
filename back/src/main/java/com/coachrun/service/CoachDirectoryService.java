package com.coachrun.service;

import com.coachrun.dto.response.CoachCertificationResponse;
import com.coachrun.dto.response.CoachFacetsResponse;
import com.coachrun.dto.response.CoachOfferResponse;
import com.coachrun.dto.response.PageResponse;
import com.coachrun.dto.response.PublicCoachDetailResponse;
import com.coachrun.dto.response.PublicCoachSummaryResponse;
import com.coachrun.entity.CoachOffer;
import com.coachrun.entity.CoachProfile;
import com.coachrun.entity.enums.CoachProfileStatus;
import com.coachrun.entity.enums.CoachSpecialty;
import com.coachrun.entity.enums.Discipline;
import com.coachrun.exception.NotFoundException;
import com.coachrun.repository.CoachCertificationRepository;
import com.coachrun.repository.CoachOfferRepository;
import com.coachrun.repository.CoachPhotoRepository;
import com.coachrun.repository.CoachProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * L'annuaire public des coachs : recherche, facettes, fiche.
 *
 * <h2>L'ordre par défaut, et pourquoi il tourne</h2>
 *
 * <p>Ni note (il n'y en a pas au lancement), ni ancienneté : trier par date d'arrivée figerait les
 * positions le premier jour, et les cinq premiers inscrits capteraient l'essentiel des demandes
 * pour toujours. L'ordre est donc : ceux qui <b>acceptent des athlètes</b> d'abord — proposer en
 * tête quelqu'un qu'on ne peut pas solliciter est une perte de temps pour tout le monde — puis un
 * <b>mélange à graine quotidienne</b>. Deux coachs équivalents passent chacun leur tour en tête,
 * l'ordre est stable au sein d'une même journée (feuilleter ne rebat pas les cartes sous les
 * doigts), et il change le lendemain.</p>
 *
 * <h2>Le tri se fait en mémoire, et c'est un choix daté</h2>
 *
 * <p>Un mélange déterministe par graine ne s'écrit pas en JPQL, et sa transcription SQL n'est pas
 * portable entre H2 et PostgreSQL. Pour un annuaire de quelques dizaines de fiches — l'ouverture
 * est prévue à dix coachs — charger les correspondances, les ordonner puis les paginer coûte moins
 * qu'une fonction propriétaire à maintenir en double. {@link #MAX_MATCHES} borne ce chargement, et
 * le journal prévient quand la borne est atteinte : c'est le signal qu'il faudra passer le tri en
 * base, pas un jour arbitraire.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CoachDirectoryService {

    /** Les fiches qu'un visiteur peut voir : publiées, et celles qui ne prennent plus d'athlètes. */
    private static final Set<CoachProfileStatus> VISIBLE =
            Set.of(CoachProfileStatus.PUBLISHED, CoachProfileStatus.CLOSED);

    /**
     * Plafond de correspondances chargées avant tri.
     *
     * <p>Atteint, il ne casse rien — la recherche rend les premières fiches — mais il fausse
     * l'équité du mélange, puisque les fiches au-delà ne participent plus au tirage. C'est
     * précisément le moment de passer le tri en base, et le journal le dit.</p>
     */
    private static final int MAX_MATCHES = 500;

    private final CoachProfileRepository profileRepository;
    private final CoachOfferRepository offerRepository;
    private final CoachCertificationRepository certificationRepository;
    private final CoachPhotoRepository photoRepository;

    /**
     * Cherche dans l'annuaire.
     *
     * <p>Tous les critères sont facultatifs et se cumulent. Aucun ne peut faire apparaître une
     * fiche non publiée : le filtre de visibilité est appliqué en premier et n'est pas paramétrable.</p>
     */
    public PageResponse<PublicCoachSummaryResponse> search(DirectoryQuery query, int page, int size) {
        List<CoachProfile> visible = profileRepository.findByStatusIn(VISIBLE,
                org.springframework.data.domain.PageRequest.of(0, MAX_MATCHES)).getContent();
        if (visible.size() == MAX_MATCHES) {
            log.warn("Annuaire : {} fiches chargées, le plafond est atteint — le tri doit passer "
                    + "en base pour que le mélange reste équitable.", MAX_MATCHES);
        }

        List<CoachProfile> matches = visible.stream()
                .filter(query::matches)
                .filter(p -> withinBudget(p, query.maxMonthlyCents()))
                .toList();
        List<CoachProfile> ordered = order(matches);

        int from = Math.min(page * size, ordered.size());
        int to = Math.min(from + size, ordered.size());
        List<PublicCoachSummaryResponse> content = ordered.subList(from, to).stream()
                .map(this::summary)
                .toList();

        int totalPages = size == 0 ? 0 : (int) Math.ceil((double) ordered.size() / size);
        return new PageResponse<>(content, page, size, ordered.size(), totalPages);
    }

    /**
     * Le repli quand une recherche ne rend rien : les coachs qui prennent des athlètes.
     *
     * <p>Séparé de {@link #search} plutôt que déclenché à l'intérieur : un résultat de repli n'est
     * pas un résultat de recherche, et le mélanger aux autres laisserait croire que le filtre a
     * fonctionné. L'écran appelle cette route en connaissance de cause, et dit pourquoi.</p>
     */
    public PageResponse<PublicCoachSummaryResponse> fallback(int size) {
        return search(new DirectoryQuery(null, null, null, null, null, null, true), 0, size);
    }

    public PublicCoachDetailResponse bySlug(String slug) {
        CoachProfile p = profileRepository.findBySlug(slug)
                .filter(profile -> VISIBLE.contains(profile.getStatus()))
                // Le même message qu'une fiche inexistante : distinguer « n'existe pas » de
                // « existe mais n'est pas publiée » apprendrait à un visiteur qu'un coach est
                // inscrit sans l'avoir voulu.
                .orElseThrow(() -> new NotFoundException("Cette fiche n'existe pas ou n'est plus publiée."));
        return PublicCoachDetailResponse.of(p, photoUrl(p),
                certificationRepository.findByProfileIdOrderByObtainedYearDesc(p.getId())
                        .stream().map(CoachCertificationResponse::from).toList(),
                activeOffers(p.getId()).stream().map(CoachOfferResponse::from).toList());
    }

    /**
     * Les facettes et leurs comptes.
     *
     * <p>Calculées sur l'ensemble visible, pas sur ce que rendraient les autres filtres déjà
     * cochés — cf. {@link CoachFacetsResponse}. C'est ce qui permet à l'écran de désactiver une
     * valeur qui ne rendrait rien, plutôt que de la proposer et de décevoir.</p>
     */
    public CoachFacetsResponse facets() {
        List<CoachProfile> visible = profileRepository.findByStatusIn(VISIBLE,
                org.springframework.data.domain.PageRequest.of(0, MAX_MATCHES)).getContent();

        Map<String, Long> disciplines = new LinkedHashMap<>();
        Map<String, Long> specialties = new LinkedHashMap<>();
        Map<String, Long> languages = new LinkedHashMap<>();
        Map<String, Long> cities = new LinkedHashMap<>();

        for (CoachProfile p : visible) {
            p.getDisciplines().forEach(d -> disciplines.merge(d.name(), 1L, Long::sum));
            p.getSpecialties().forEach(s -> specialties.merge(s.name(), 1L, Long::sum));
            p.getLanguages().forEach(l -> languages.merge(l, 1L, Long::sum));
            if (StringUtils.hasText(p.getCity())) {
                cities.merge(p.getCity().trim(), 1L, Long::sum);
            }
        }

        long accepting = visible.stream().filter(p -> p.getStatus().acceptsRequests()).count();
        return new CoachFacetsResponse(
                // Les disciplines et spécialités sont rendues EN ENTIER, comptes à zéro compris :
                // c'est ce qui permet à l'écran de les afficher grisées plutôt que de les faire
                // disparaître. Une facette qui disparaît laisse croire qu'elle n'existe pas.
                allDisciplines(disciplines),
                allSpecialties(specialties),
                sortedByCount(languages, CoachDirectoryService::languageLabel),
                sortedByCount(cities, c -> c),
                visible.size(),
                accepting);
    }

    // ------------------------------------------------------------------ interne

    /**
     * Ceux qui acceptent des athlètes d'abord, puis mélange à graine du jour.
     *
     * <p>La graine est la date : l'ordre ne bouge pas d'une requête à l'autre dans la même
     * journée — sans quoi feuilleter ferait réapparaître des fiches déjà vues et en sauterait
     * d'autres — et il tourne le lendemain.</p>
     */
    private List<CoachProfile> order(List<CoachProfile> matches) {
        List<CoachProfile> shuffled = new ArrayList<>(matches);
        Collections.shuffle(shuffled, new Random(LocalDate.now().toEpochDay()));
        shuffled.sort(Comparator.comparing(
                (CoachProfile p) -> p.getStatus().acceptsRequests() ? 0 : 1));
        return shuffled;
    }

    /**
     * La fiche tient-elle dans le budget demandé ?
     *
     * <p>Comparé au tarif « à partir de », c'est-à-dire au <b>même nombre que celui affiché</b> sur
     * la carte du coach. Filtrer sur une autre base — la formule la plus chère, une moyenne —
     * écarterait des fiches dont le prix visible respecte pourtant le plafond, ce qui est
     * incompréhensible côté visiteur.</p>
     *
     * <p>Un coach sans aucune formule mensuelle comparable (uniquement à la séance, ou au forfait)
     * est écarté dès qu'un budget est demandé : lui inventer une mensualité serait afficher un
     * prix qu'il n'a pas annoncé.</p>
     */
    private boolean withinBudget(CoachProfile p, Integer maxMonthlyCents) {
        if (maxMonthlyCents == null) {
            return true;
        }
        Integer from = fromMonthlyCents(p.getId());
        return from != null && from <= maxMonthlyCents;
    }

    private PublicCoachSummaryResponse summary(CoachProfile p) {
        return PublicCoachSummaryResponse.of(p, fromMonthlyCents(p.getId()), photoUrl(p));
    }

    /**
     * Le tarif « à partir de », ramené au mois.
     *
     * <p>Les formules à la séance et les forfaits uniques sont écartés : les ramener au mois
     * demanderait d'inventer un nombre de séances, et un prix inventé affiché sur une vitrine
     * publique est pire que pas de prix du tout.</p>
     */
    private Integer fromMonthlyCents(UUID profileId) {
        return activeOffers(profileId).stream()
                .map(o -> o.getPeriodicity().monthlyEquivalentCents(o.getAmountCents()))
                .filter(java.util.Objects::nonNull)
                .min(Integer::compareTo)
                .orElse(null);
    }

    private List<CoachOffer> activeOffers(UUID profileId) {
        return offerRepository.findByProfileIdAndActiveTrueOrderByPositionAscCreatedAtAsc(profileId);
    }

    private String photoUrl(CoachProfile p) {
        return photoRepository.findIdByProfileId(p.getId())
                .map(id -> "/public/coach-photos/" + id)
                .orElse(null);
    }

    private static List<CoachFacetsResponse.FacetValue> allDisciplines(Map<String, Long> counts) {
        return java.util.Arrays.stream(Discipline.values())
                .map(d -> new CoachFacetsResponse.FacetValue(
                        d.name(), disciplineLabel(d), counts.getOrDefault(d.name(), 0L)))
                .toList();
    }

    private static List<CoachFacetsResponse.FacetValue> allSpecialties(Map<String, Long> counts) {
        return CoachSpecialty.all().stream()
                .map(s -> new CoachFacetsResponse.FacetValue(
                        s.name(), s.label(), counts.getOrDefault(s.name(), 0L)))
                .toList();
    }

    /**
     * Langues et villes : seules celles qui existent, du plus fréquent au moins fréquent.
     *
     * <p>À la différence des disciplines et spécialités, ces listes ne sont pas énumérables — il y
     * a des milliers de villes. Proposer celles que personne n'a renseignées n'apprendrait rien.</p>
     */
    private static List<CoachFacetsResponse.FacetValue> sortedByCount(
            Map<String, Long> counts, java.util.function.Function<String, String> label) {
        return counts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed()
                        .thenComparing(Map.Entry.comparingByKey()))
                .map(e -> new CoachFacetsResponse.FacetValue(e.getKey(), label.apply(e.getKey()), e.getValue()))
                .toList();
    }

    private static String disciplineLabel(Discipline d) {
        return switch (d) {
            case ROUTE -> "Route";
            case TRAIL -> "Trail";
        };
    }

    private static String languageLabel(String code) {
        return switch (code.toLowerCase(Locale.ROOT)) {
            case "fr" -> "Français";
            case "en" -> "Anglais";
            case "es" -> "Espagnol";
            case "de" -> "Allemand";
            case "it" -> "Italien";
            case "pt" -> "Portugais";
            default -> code.toUpperCase(Locale.ROOT);
        };
    }

    /**
     * Les critères d'une recherche.
     *
     * <p>Le filtrage vit ici, en mémoire, pour la même raison que le tri (cf. l'en-tête de classe) :
     * l'annuaire tient dans une page, et un {@code Specification} qui joint quatre tables de
     * facettes coûterait plus à écrire et à relire qu'il ne rapporte à cette échelle.</p>
     */
    public record DirectoryQuery(
            Discipline discipline,
            CoachSpecialty specialty,
            String language,
            String city,
            Boolean remote,
            /**
             * Plafond de tarif mensuel, en centimes.
             *
             * <p>Il n'est PAS appliqué ici : il porte sur les formules, que cette description de
             * critères n'a pas sous la main. Le service l'applique après avoir calculé le tarif
             * « à partir de » — c'est le même nombre que celui affiché, ce qui évite qu'une fiche
             * soit écartée par un prix différent de celui qu'on lui voit.</p>
             */
            Integer maxMonthlyCents,
            /** Vrai pour ne garder que ceux qui prennent des athlètes. */
            Boolean acceptingOnly) {

        boolean matches(CoachProfile p) {
            if (discipline != null && !p.getDisciplines().contains(discipline)) {
                return false;
            }
            if (specialty != null && !p.getSpecialties().contains(specialty)) {
                return false;
            }
            if (StringUtils.hasText(language)
                    && !p.getLanguages().contains(language.toLowerCase(Locale.ROOT))) {
                return false;
            }
            if (StringUtils.hasText(city)
                    && (p.getCity() == null || !p.getCity().equalsIgnoreCase(city.trim()))) {
                return false;
            }
            // « À distance » ne veut pas dire « pas de présentiel » : un coach peut faire les deux,
            // et le filtre demande seulement qu'il propose le mode coché.
            if (Boolean.TRUE.equals(remote) && !p.isRemote()) {
                return false;
            }
            if (Boolean.FALSE.equals(remote) && !p.isInPerson()) {
                return false;
            }
            return !Boolean.TRUE.equals(acceptingOnly) || p.getStatus().acceptsRequests();
        }
    }
}
