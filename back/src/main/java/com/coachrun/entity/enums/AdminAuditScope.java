package com.coachrun.entity.enums;

/**
 * Famille d'actions du journal. Se déduit de l'action, ne se stocke pas.
 *
 * <h2>Pourquoi cette notion est apparue</h2>
 *
 * <p>Le journal ne consignait que les gestes du back-office : une poignée de lignes par semaine,
 * toutes faites par un administrateur. Il consigne désormais aussi ce que font les utilisateurs
 * ordinaires — connexions, mots de passe, consentements, suppressions d'athlètes. Le volume
 * change d'ordre de grandeur : une seule journée de connexions dépasse une année de gestes
 * d'administration.</p>
 *
 * <p>Sans séparation, deux écrans existants se seraient vidés de leur sens : le bandeau
 * « dernières actions » du tableau de bord n'aurait plus montré que des connexions, et le compteur
 * « actions d'administration (7 j) » aurait compté tout autre chose que ce que son libellé annonce.
 * La portée permet de garder ces deux lectures intactes tout en ouvrant le journal au reste.</p>
 *
 * <h2>Pourquoi elle n'est pas une colonne</h2>
 *
 * <p>Elle se déduit entièrement de {@link AdminAuditAction}, exactement comme
 * {@code sensitive()}. La stocker obligerait à une migration, puis à un rattrapage des lignes
 * déjà écrites, pour une information qui ne dit rien de plus que l'action elle-même. Déduite,
 * elle vaut immédiatement pour tout l'historique : les entrées antérieures portent toutes des
 * gestes d'administration, et c'est bien ce que la déduction leur attribue.</p>
 */
public enum AdminAuditScope {

    /** Gestes du back-office : comptes, clubs, plateforme. Le journal d'origine. */
    ADMINISTRATION("Administration"),

    /** Ce qui ouvre ou protège un accès : connexion, échec, mot de passe, adresse e-mail. */
    SECURITY("Sécurité des accès"),

    /** Consentement santé, portabilité, effacement, montre connectée — les droits RGPD. */
    PRIVACY("Données personnelles"),

    /** Gestes structurants d'un coach : création, archivage, invitation, retrait. */
    COACHING("Coaching");

    private final String label;

    AdminAuditScope(String label) {
        this.label = label;
    }

    /** Libellé français, affiché tel quel par le back-office. */
    public String label() {
        return label;
    }
}
