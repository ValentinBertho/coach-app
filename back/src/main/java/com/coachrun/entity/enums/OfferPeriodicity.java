package com.coachrun.entity.enums;

/**
 * À quel rythme se paie une formule de coaching.
 *
 * <p>Rien n'est encaissé sur la plateforme : ces valeurs servent à <b>afficher</b> un tarif et à
 * le filtrer dans l'annuaire. Elles existent dès maintenant parce que le tarif est un critère de
 * recherche, et parce que le rétro-ajouter aurait obligé à reprendre les fiches déjà écrites.</p>
 */
public enum OfferPeriodicity {

    MONTHLY,
    QUARTERLY,
    YEARLY,
    PER_SESSION,
    /** Forfait à la prestation : un plan pour un objectif, payé une fois. */
    ONE_OFF;

    /** Suffixe affiché après le montant (« 90 € / mois »). */
    public String suffix() {
        return switch (this) {
            case MONTHLY -> "/ mois";
            case QUARTERLY -> "/ trimestre";
            case YEARLY -> "/ an";
            case PER_SESSION -> "/ séance";
            case ONE_OFF -> "le forfait";
        };
    }

    /**
     * Montant ramené au mois, en centimes — la seule façon de comparer des formules qui ne se
     * paient pas au même rythme, et donc de trier ou filtrer l'annuaire par tarif.
     *
     * <p>{@link #PER_SESSION} et {@link #ONE_OFF} n'ont pas d'équivalent mensuel honnête : le
     * premier dépend d'un nombre de séances qu'on ne connaît pas, le second ne se répète pas.
     * Ils rendent {@code null}, et le filtre « prix » les écarte plutôt que de leur inventer une
     * mensualité.</p>
     */
    public Integer monthlyEquivalentCents(int amountCents) {
        return switch (this) {
            case MONTHLY -> amountCents;
            case QUARTERLY -> Math.round(amountCents / 3f);
            case YEARLY -> Math.round(amountCents / 12f);
            case PER_SESSION, ONE_OFF -> null;
        };
    }

    public String label() {
        return switch (this) {
            case MONTHLY -> "Par mois";
            case QUARTERLY -> "Par trimestre";
            case YEARLY -> "Par an";
            case PER_SESSION -> "Par séance";
            case ONE_OFF -> "Forfait unique";
        };
    }
}
