package com.coachrun.dto.response;

import com.coachrun.entity.enums.MetricFormat;
import com.coachrun.entity.enums.MetricUnit;
import com.coachrun.entity.enums.ZoneAnchor;
import com.coachrun.entity.enums.ZoneValueSource;

import java.util.List;
import java.util.UUID;

/**
 * L'échelle de zones d'un athlète, telle qu'il la lit lui-même (portail {@code /me}).
 *
 * <p>Volontairement <b>composée côté serveur</b>, là où le coach assemble trois catalogues de club
 * (zones, métriques, valeurs) dans son navigateur. Deux raisons : l'athlète n'a besoin que de ce
 * qui le concerne — trois appels et une jointure client pour afficher un tableau de lecture
 * seraient un coût sans contrepartie —, et surtout ces catalogues sont la configuration du club.
 * Les exposer entiers pour en extraire ses propres lignes reviendrait à lui ouvrir le paramétrage
 * de son club pour lui montrer ses allures.</p>
 *
 * <p>La règle ({@code anchor} / {@code lowPct} / {@code highPct}) accompagne chaque valeur : sans
 * elle, une fourchette d'allures est un chiffre tombé du ciel. Avec elle, l'athlète lit « 88–92 %
 * de mon seuil » et sait à quoi la rattacher — et pourquoi elle bouge quand son seuil bouge.</p>
 */
public record AthleteZoneSheetResponse(
        UUID zoneId,
        String name,
        String color,
        String description,
        int sortOrder,
        List<Metric> metrics
) {

    /**
     * Une métrique de la zone : ce qu'elle mesure, la fourchette calculée pour cet athlète, et la
     * règle dont elle sort.
     *
     * @param source AUTO = calculée depuis son profil ; MANUAL = fixée par son coach. La distinction
     *               est lisible pour lui : elle dit si la valeur suivra son prochain test.
     */
    public record Metric(
            UUID metricTypeId,
            String code,
            String name,
            MetricUnit unit,
            MetricFormat format,
            Double valueMin,
            Double valueMax,
            ZoneValueSource source,
            ZoneAnchor anchor,
            ZoneAnchor highAnchor,
            Double lowPct,
            Double highPct
    ) {
    }
}
