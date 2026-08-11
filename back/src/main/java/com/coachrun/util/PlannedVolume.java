package com.coachrun.util;

/**
 * Ce qui compte comme volume prévu d'une séance — et ce qui n'en est que le résidu.
 *
 * <p><b>Le problème.</b> Le total d'une séance n'additionne que ce que le calcul sait convertir :
 * un bloc dont l'allure se calcule donne sa distance, un bloc de durée sans allure n'apporte rien.
 * Une séance écrite « footing 55' », prescrite à un athlète dont le profil ne fournit aucune
 * allure, ressort donc avec pour tout volume ses éducatifs — une centaine de mètres et quelques
 * secondes. Ce n'est pas une approximation du volume de la séance : c'est autre chose.</p>
 *
 * <p><b>La règle.</b> Un seuil plancher plutôt qu'un contrôle de cohérence. On a d'abord jugé la
 * crédibilité sur l'allure implicite (durée ÷ distance), mais 100 m en 20 secondes donne 3'20/km —
 * une allure parfaitement plausible, pour une séance qui ne l'est pas du tout. Le résidu ne se
 * reconnaît pas à son incohérence, il se reconnaît à sa <b>taille</b> : sous 500 mètres ou sous
 * trois minutes, aucune séance de course à pied n'est décrite, échauffement compris.</p>
 *
 * <p>Une grandeur écartée est traitée comme <b>absente</b>, jamais comme fausse : les écrans
 * estiment alors le volume depuis la durée, le rapprochement s'abstient de comparer, et les
 * totaux hebdomadaires cessent de compter zéro. La séance prescrite, elle, n'est pas modifiée.</p>
 */
public final class PlannedVolume {

    /** Sous ce seuil, le total ne décrit pas une séance de course à pied mais ses éducatifs. */
    public static final int MIN_SESSION_DISTANCE_M = 500;

    /** Idem pour la durée : aucune séance prescrite ne dure moins de trois minutes. */
    public static final int MIN_SESSION_DURATION_S = 180;

    private PlannedVolume() {
    }

    /** Distance prévue si elle décrit bien une séance, {@code null} sinon. */
    public static Integer usableDistanceM(Integer distanceM) {
        return distanceM != null && distanceM >= MIN_SESSION_DISTANCE_M ? distanceM : null;
    }

    /** Durée prévue si elle décrit bien une séance, {@code null} sinon. */
    public static Integer usableDurationS(Integer durationS) {
        return durationS != null && durationS >= MIN_SESSION_DURATION_S ? durationS : null;
    }

    /**
     * Distance à retenir pour une séance : la distance prévue si elle est exploitable, sinon une
     * estimation depuis la durée et l'allure de référence de l'athlète.
     *
     * <p>C'est un ordre de grandeur, pas une cible — les écrans le signalent par un « ≈ ». Sans
     * durée exploitable ni allure de référence, on rend {@code null} : compter zéro kilomètre
     * pour une heure de course fausse le volume hebdomadaire dans le sens qui inquiète le moins,
     * mais le fausse quand même.</p>
     *
     * @param referencePaceSPerKm allure d'endurance de l'athlète (s/km), ou {@code null}
     */
    public static Integer distanceOrEstimate(Integer distanceM, Integer durationS,
                                             Integer referencePaceSPerKm) {
        Integer usable = usableDistanceM(distanceM);
        if (usable != null) {
            return usable;
        }
        Integer seconds = usableDurationS(durationS);
        if (seconds == null || referencePaceSPerKm == null || referencePaceSPerKm <= 0) {
            return null;
        }
        // Arrondi à la centaine de mètres : une estimation ne se présente pas au mètre près.
        return (int) (Math.round(seconds * 1000.0 / referencePaceSPerKm / 100.0) * 100);
    }
}
