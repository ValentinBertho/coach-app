package com.coachrun.entity.enums;

/**
 * Ce qu'on reproche à une fiche coach.
 *
 * <p>Une liste fermée plutôt qu'un champ libre seul, pour une raison de tri : l'administrateur qui
 * ouvre sa file le matin doit voir en un coup d'œil ce qui relève de l'urgence — une usurpation
 * d'identité, un diplôme inventé — et ce qui relève de la retouche. Un motif reste néanmoins
 * accompagné d'un texte : la catégorie oriente, elle n'explique pas.</p>
 *
 * <p>{@link #FALSE_CREDENTIALS} est le motif qui justifie à lui seul l'existence de ce dispositif.
 * La décision 4 affiche les diplômes comme déclarés, sans vérification ; il fallait donc au moins
 * que quelqu'un puisse dire « celui-là, il ne l'a pas ».</p>
 */
public enum CoachReportReason {

    /** Un diplôme, une certification ou une carte professionnelle que le coach n'a pas. */
    FALSE_CREDENTIALS,

    /** La fiche se présente sous l'identité de quelqu'un d'autre. */
    IMPERSONATION,

    /** Propos déplacés, photo inappropriée, texte offensant. */
    INAPPROPRIATE_CONTENT,

    /** Publicité, redirection hors plateforme, contenu sans rapport avec le coaching. */
    SPAM,

    /**
     * Une pratique dangereuse : conseils nutritionnels ou médicaux hors compétence, incitation à
     * s'entraîner blessé. C'est le motif le plus grave du lot, et le plus difficile à trancher.
     */
    DANGEROUS_ADVICE,

    /** Le reste, qui existe toujours. */
    OTHER;

    public String label() {
        return switch (this) {
            case FALSE_CREDENTIALS -> "Diplôme ou certification inexacte";
            case IMPERSONATION -> "Usurpation d'identité";
            case INAPPROPRIATE_CONTENT -> "Contenu inapproprié";
            case SPAM -> "Publicité ou spam";
            case DANGEROUS_ADVICE -> "Conseils dangereux";
            case OTHER -> "Autre";
        };
    }
}
