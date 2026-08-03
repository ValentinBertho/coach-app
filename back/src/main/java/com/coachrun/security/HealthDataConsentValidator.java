package com.coachrun.security;

import com.coachrun.entity.Athlete;
import com.coachrun.exception.ForbiddenException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Garde de la base légale : refuse la collecte d'une donnée de l'article 9 quand l'athlète n'a
 * pas — ou plus — consenti.
 *
 * <p><b>Pourquoi.</b> Le consentement était traité comme une case franchie à l'entrée : il était
 * écrit à l'acceptation de l'invitation, puis jamais consulté avant d'écrire quoi que ce soit.
 * Deux conséquences, toutes deux réelles sur le parcours normal du produit. D'abord, le coach
 * crée un athlète, remplit son profil et ses tests, <em>puis</em> l'invite : toutes les données
 * saisies avant l'acceptation — et toutes celles d'un athlète qui n'accepte jamais — n'ont
 * aucune base légale. Ensuite, une fois le retrait implémenté (art. 7-3), rien n'aurait empêché
 * de continuer à collecter le lendemain du retrait.</p>
 *
 * <p>Ce validateur suit la même forme que {@link ClubAccessValidator} et
 * {@link AthleteAccessValidator} : une garde explicite, appelée au début des services concernés,
 * qui lève un 403 nommant la cause. Le message est écrit pour le coach, qui est presque toujours
 * celui qui déclenche le refus, et qui n'a aucun moyen de deviner la règle autrement.</p>
 *
 * <p>Le périmètre est celui de la politique de confidentialité publiée : mesures de lactate,
 * niveaux de douleur et de fatigue, indisponibilités pour raison médicale, notes médicales. Les
 * allures, distances, fréquences cardiaques et RPE relèvent de l'exécution du contrat et ne
 * passent donc pas par ici.</p>
 */
@Slf4j
@Component
public class HealthDataConsentValidator {

    /**
     * Vérifie que l'athlète autorise le traitement de ses données de santé.
     *
     * @param athlete   athlète concerné
     * @param dataLabel nature de la donnée refusée, reprise telle quelle dans le message
     * @throws ForbiddenException si le consentement est absent ou retiré
     */
    public void requireConsent(Athlete athlete, String dataLabel) {
        if (athlete == null || athlete.isHealthDataConsentActive()) {
            return;
        }
        String name = athlete.getFirstName() != null ? athlete.getFirstName() : "Cet athlète";
        if (athlete.getHealthDataConsentAt() == null) {
            throw new ForbiddenException(name + " n'a pas encore accepté son invitation : "
                    + "tant qu'il n'a pas consenti au traitement de ses données de santé, "
                    + dataLabel + " ne peut pas être enregistré.");
        }
        throw new ForbiddenException(name + " a retiré son consentement au traitement de ses "
                + "données de santé : " + dataLabel + " ne peut plus être enregistré.");
    }

    /** Vrai si la collecte est autorisée (variante non levante, pour un affichage conditionnel). */
    public boolean isAllowed(Athlete athlete) {
        return athlete != null && athlete.isHealthDataConsentActive();
    }

    /**
     * Valeur de santé <b>déclarée par l'athlète sur lui-même</b> : conservée si la collecte est
     * autorisée, écartée sinon.
     *
     * <p><b>Pourquoi deux régimes.</b> {@link #requireConsent} refuse par une 403, ce qui est le bon
     * comportement quand c'est le <em>coach</em> qui saisit : il doit savoir pourquoi son geste est
     * refusé, et il a un recours (relancer l'invitation, demander le consentement). Appliquer la
     * même règle au retour de séance de l'athlète casserait la boucle quotidienne : le RPE, le
     * statut et le commentaire relèvent de l'exécution du contrat et doivent continuer de passer.
     * On applique donc ici la minimisation plutôt que le refus — la séance se clôture, seules la
     * fatigue et la douleur ne sont pas enregistrées.</p>
     *
     * <p>Côté interface, ces champs ne devraient pas être proposés à un athlète qui a retiré son
     * consentement ; cette méthode est la ceinture, pas les bretelles.</p>
     */
    public Integer keepIfAllowed(Athlete athlete, Integer declaredValue) {
        return isAllowed(athlete) ? declaredValue : null;
    }
}
