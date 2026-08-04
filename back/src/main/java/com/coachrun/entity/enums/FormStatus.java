package com.coachrun.entity.enums;

/**
 * État de forme d'un athlète (cf. DARI Lab). Calculé exclusivement à partir de la
 * <strong>fatigue + douleur</strong> (jamais du RPE de séance).
 */
public enum FormStatus {
    GREEN,
    ORANGE,
    RED,
    /**
     * Aucun signal récent : le dernier retour est trop ancien pour décrire l'état d'aujourd'hui.
     *
     * <p>Sans cet état, un athlète ayant déclaré fatigue 9 et douleur 6 après une compétition
     * restait rouge indéfiniment s'il cessait de saisir — et remontait en tête de la file
     * « à surveiller », devant un athlète qui a signalé une gêne le matin même. Un retour vieux de
     * six semaines ne dit rien de l'état physique du jour, ni en mal ni en bien : il ne faut donc
     * ni le peindre en rouge, ni le blanchir en vert.</p>
     */
    STALE
}
