package com.coachrun.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * La frontière entre « Strava a nommé cette sortie » et « quelqu'un l'a nommée ».
 *
 * <p>Tout ce que fait cette classe repose sur cette frontière, et l'erreur n'y est pas symétrique.
 * Ne pas reconnaître un nom généré coûte une ligne fade dans un calendrier. Reconnaître à tort un
 * nom écrit par un athlète l'efface — et comme rien n'est conservé de l'original, l'efface pour de
 * bon. Ces tests poussent donc surtout du côté du refus.</p>
 */
class StravaAutoNameTest {

    /** Les gabarits anglais, dans les deux sens de casse et d'espacement qu'on rencontre. */
    @Test
    void englishTemplatesAreRecognized() {
        assertThat(StravaAutoName.sportOf("Morning Run")).contains("Course à pied");
        assertThat(StravaAutoName.sportOf("Evening Ride")).contains("Vélo");
        assertThat(StravaAutoName.sportOf("lunch swim")).contains("Natation");
        assertThat(StravaAutoName.sportOf("  Afternoon   Walk  ")).contains("Marche");
        assertThat(StravaAutoName.sportOf("Night Weight Training")).contains("Musculation");
    }

    /** Strava nomme dans la langue du compte de l'athlète, pas dans celle de l'application. */
    @Test
    void frenchTemplatesAreRecognized() {
        assertThat(StravaAutoName.sportOf("Course à pied le matin")).contains("Course à pied");
        assertThat(StravaAutoName.sportOf("Sortie à vélo l'après-midi")).contains("Vélo");
        assertThat(StravaAutoName.sportOf("Natation le soir")).contains("Natation");
    }

    /**
     * Les moments du soir et de la nuit, que la liste française ignorait.
     *
     * <p>Régression constatée en bêta : une sortie du soir importée d'un compte Strava en
     * français s'appelait « Course à pied en soirée » et n'était <b>pas</b> renommée, alors que
     * la même sortie sur un compte en anglais (« Evening Run ») l'était. La liste des tournures
     * était tenue à la main et « en soirée » n'y figurait pas : le titre passait donc pour choisi
     * par l'athlète, et la séance rapprochée ne lui donnait jamais son nom.</p>
     */
    @Test
    void frenchEveningAndNightTemplatesAreRecognized() {
        assertThat(StravaAutoName.sportOf("Course à pied en soirée")).contains("Course à pied");
        assertThat(StravaAutoName.sportOf("Sortie à vélo en soirée")).contains("Vélo");
        assertThat(StravaAutoName.sportOf("Course à pied de nuit")).contains("Course à pied");
        assertThat(StravaAutoName.sportOf("Course à pied le midi")).contains("Course à pied");
    }

    /**
     * L'apostrophe typographique de Strava (« l’après-midi ») et les accents ne doivent pas
     * décider du sort d'un titre.
     *
     * <p>Même famille de défaut que ci-dessus : la comparaison portait sur les octets, si bien
     * qu'il fallait énumérer « l'après-midi », « l'apres-midi »… et que le jour où Strava écrit
     * l'apostrophe courbe, plus rien ne correspond. Le titre est désormais ramené à une forme
     * sans accent et à apostrophe droite avant comparaison.</p>
     */
    @Test
    void accentsAndTypographicApostrophesDoNotDecide() {
        assertThat(StravaAutoName.sportOf("Course à pied l’après-midi")).contains("Course à pied");
        assertThat(StravaAutoName.sportOf("COURSE A PIED EN SOIREE")).contains("Course à pied");
        assertThat(StravaAutoName.sportOf("Randonnée le matin")).contains("Randonnée");
    }

    /**
     * Le cœur du sujet : la correspondance porte sur la chaîne <b>entière</b>. « Morning Run »
     * est un nom subi, « Morning Run avec Paul » est un nom choisi — et il contient pourtant le
     * gabarit mot pour mot. Une reconnaissance par sous-chaîne les confondrait.
     */
    @Test
    void aNameSomebodyWroteIsNeverRecognized() {
        assertThat(StravaAutoName.sportOf("Morning Run avec Paul")).isEmpty();
        assertThat(StravaAutoName.sportOf("Course à pied le matin — 30/30")).isEmpty();
        assertThat(StravaAutoName.sportOf("Sortie longue")).isEmpty();
        assertThat(StravaAutoName.sportOf("Fractionné 10x400")).isEmpty();
        assertThat(StravaAutoName.sportOf("Run")).isEmpty();
        assertThat(StravaAutoName.sportOf("Morning")).isEmpty();
        // Les tournures ajoutées pour le soir ne doivent pas se mettre à mordre sur des titres
        // qu'on écrit vraiment : c'est le risque exact que fait courir une liste qu'on élargit.
        assertThat(StravaAutoName.sportOf("Course à pied en soirée avec Paul")).isEmpty();
        assertThat(StravaAutoName.sportOf("Sortie du soir, bonsoir")).isEmpty();
        assertThat(StravaAutoName.sportOf("S1 2x15'")).isEmpty();
    }

    /** Un titre absent n'est pas un titre généré : il n'y a rien à remplacer, et rien à décider. */
    @Test
    void anAbsentTitleIsNotRecognized() {
        assertThat(StravaAutoName.sportOf(null)).isEmpty();
        assertThat(StravaAutoName.sportOf("")).isEmpty();
        assertThat(StravaAutoName.sportOf("   ")).isEmpty();
        assertThat(StravaAutoName.isAutoGenerated(null)).isFalse();
        assertThat(StravaAutoName.isAutoGenerated("Morning Run")).isTrue();
    }

    /** Le repli dit ce que la sortie était, avec ce qu'on en sait — la distance d'abord. */
    @Test
    void theFallbackTitlePrefersDistance() {
        assertThat(StravaAutoName.descriptiveTitle("Course à pied", 10200, 2700))
                .isEqualTo("Course à pied — 10,2 km");
    }

    /** Sans distance utilisable, la durée. Certains tapis et séances de renfort n'en ont pas. */
    @Test
    void theFallbackTitleFallsBackOnDuration() {
        assertThat(StravaAutoName.descriptiveTitle("Musculation", null, 2880))
                .isEqualTo("Musculation — 48 min");
        assertThat(StravaAutoName.descriptiveTitle("Course à pied", 40, 2880))
                .as("une distance quasi nulle est du bruit de GPS, pas une distance")
                .isEqualTo("Course à pied — 48 min");
    }

    /**
     * Et sans rien du tout, le sport seul. Inventer « 0,0 km » serait pire que le nom d'origine :
     * ce serait une affirmation fausse là où il n'y avait qu'une absence.
     */
    @Test
    void theFallbackTitleClaimsNothingItDoesNotKnow() {
        assertThat(StravaAutoName.descriptiveTitle("Yoga", null, null)).isEqualTo("Yoga");
        assertThat(StravaAutoName.descriptiveTitle("Yoga", 0, 12)).isEqualTo("Yoga");
    }
}
