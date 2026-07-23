package com.coachrun.entity.enums;

/**
 * Domaine d'une catégorie de bibliothèque : le même arbre {@code session_categories} porte les
 * catégories des trois bibliothèques (course, prépa physique, éducatifs), discriminées par ce champ.
 * Cf. AUDIT-COACH-ZONES-METRIQUES §4.1 (résidu QA1 — unification des catégories).
 */
public enum CategoryDomain {
    /** Séances course (DARI Lab). */
    COURSE,
    /** Prépa physique / renforcement. */
    STRENGTH,
    /** Éducatifs de course (gammes). */
    DRILL
}
