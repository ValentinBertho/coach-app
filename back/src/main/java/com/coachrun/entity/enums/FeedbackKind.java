package com.coachrun.entity.enums;

/**
 * Nature d'un retour de bêta. Volontairement court : trois choix se lisent d'un coup d'œil sur
 * un téléphone, et la distinction utile au tri est « est-ce cassé ou est-ce un souhait ».
 */
public enum FeedbackKind {
    /** Quelque chose ne fonctionne pas comme attendu. */
    BUG,
    /** Une suggestion, une fonctionnalité manquante. */
    IDEA,
    /** Une question d'usage — souvent le signe qu'un écran n'est pas clair. */
    QUESTION
}
