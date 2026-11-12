package com.coachrun.dto;

import com.coachrun.entity.CoachProfile;
import com.coachrun.exception.ApiException;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;

import java.net.URI;
import java.util.List;
import java.util.Locale;

/**
 * Les liens qu'un coach affiche sur sa fiche : son site et ses réseaux.
 *
 * <h2>Pourquoi des liens, alors que la fiche ne publie aucune coordonnée</h2>
 *
 * <p>Ni e-mail ni téléphone ne sortent de l'annuaire, et cela ne change pas. Un compte Instagram
 * ou Strava n'est pas une coordonnée de démarchage : c'est ce qu'un athlète regarde pour savoir à
 * qui il a affaire avant de demander. Ils ne figurent que sur la <b>fiche</b>, jamais dans la
 * liste de résultats — celle qu'on aspire page par page.</p>
 *
 * <h2>Ce qui est accepté</h2>
 *
 * <p>Uniquement du {@code https}, et pour chaque réseau uniquement son propre domaine. Un champ
 * « Instagram » qui accepterait n'importe quelle adresse deviendrait un lien publicitaire libre
 * sous une icône de confiance. Le site personnel est le seul champ au domaine libre.</p>
 */
public record CoachLinks(
        @Size(max = 300) String website,
        @Size(max = 300) String instagram,
        @Size(max = 300) String strava,
        @Size(max = 300) String youtube) {

    public static final CoachLinks NONE = new CoachLinks(null, null, null, null);

    public static CoachLinks of(CoachProfile p) {
        return new CoachLinks(p.getWebsiteUrl(), p.getInstagramUrl(), p.getStravaUrl(), p.getYoutubeUrl());
    }

    /**
     * Les mêmes liens, nettoyés et vérifiés ; lève une 400 qui nomme le champ fautif.
     *
     * <p>Un lien saisi sans {@code https://} est complété plutôt que refusé : c'est ainsi que tout
     * le monde recopie une adresse.</p>
     */
    public CoachLinks normalized() {
        return new CoachLinks(
                normalize(website, "Site web", null),
                normalize(instagram, "Instagram", List.of("instagram.com")),
                normalize(strava, "Strava", List.of("strava.com")),
                normalize(youtube, "YouTube", List.of("youtube.com", "youtu.be")));
    }

    private static String normalize(String raw, String label, List<String> allowedDomains) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String value = raw.trim();
        if (!value.contains("://")) {
            value = "https://" + value;
        }
        URI uri;
        try {
            uri = URI.create(value);
        } catch (IllegalArgumentException e) {
            throw invalid(label, "l'adresse n'est pas lisible");
        }
        String host = uri.getHost() == null ? null : uri.getHost().toLowerCase(Locale.ROOT);
        if (!"https".equalsIgnoreCase(uri.getScheme()) || host == null || !host.contains(".")
                || uri.getUserInfo() != null) {
            throw invalid(label, "l'adresse doit commencer par https://");
        }
        if (allowedDomains != null && allowedDomains.stream()
                .noneMatch(d -> host.equals(d) || host.endsWith("." + d))) {
            throw invalid(label, "l'adresse doit pointer vers " + allowedDomains.get(0));
        }
        if (allowedDomains != null && (uri.getPath() == null || uri.getPath().length() <= 1)) {
            // « instagram.com » tout court n'est le compte de personne.
            throw invalid(label, "il manque le nom du compte");
        }
        return uri.toASCIIString();
    }

    private static ApiException invalid(String label, String why) {
        return new ApiException(HttpStatus.BAD_REQUEST, "Lien « " + label + " » invalide : " + why + ".");
    }
}
