package com.coachrun.util;

import java.util.Locale;

/**
 * Ce qu'un {@code User-Agent} dit de l'appareil : famille, système, navigateur.
 *
 * <h2>Pourquoi c'est dérivé et non stocké</h2>
 *
 * <p>La base garde la chaîne brute ; ce classement se recalcule à chaque lecture. Un verdict figé
 * en colonne deviendrait faux le jour où la règle s'affine — et le corriger demanderait de
 * réécrire des lignes, ce que §4 bis interdit. Ici, améliorer la reconnaissance suffit à
 * requalifier tout l'historique, sans toucher à une seule donnée.</p>
 *
 * <h2>Ce qu'on en attend, et ce qu'on n'en attend pas</h2>
 *
 * <p>La question à laquelle ce classement répond est « cette personne travaille-t-elle sur un
 * téléphone ou devant un ordinateur ? » — parce que la réponse change ce qu'on lui conseille, et
 * ce qu'on soupçonne quand elle dit que l'écran est illisible. Ce n'est <b>pas</b> une
 * identification : un {@code User-Agent} se falsifie, et deux personnes du même bureau ont le
 * même. On ne s'en sert jamais pour décider d'un accès.</p>
 *
 * <p>Un agent inconnu rend {@link Platform#UNKNOWN} plutôt qu'un classement au hasard : « on ne
 * sait pas » est une réponse, « ordinateur » par défaut n'en est pas une.</p>
 */
public final class ClientProfile {

    /** Famille d'appareil, telle qu'un humain la nommerait. */
    public enum Platform {
        MOBILE("Mobile"),
        TABLET("Tablette"),
        DESKTOP("Ordinateur"),
        UNKNOWN("Inconnu");

        private final String label;

        Platform(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    private final Platform platform;
    private final String os;
    private final String browser;

    private ClientProfile(Platform platform, String os, String browser) {
        this.platform = platform;
        this.os = os;
        this.browser = browser;
    }

    public Platform platform() {
        return platform;
    }

    /** Système d'exploitation en clair (« iOS », « Android », « Windows »), ou {@code null}. */
    public String os() {
        return os;
    }

    /** Navigateur en clair (« Chrome », « Safari »), ou {@code null}. */
    public String browser() {
        return browser;
    }

    private static final ClientProfile UNKNOWN =
            new ClientProfile(Platform.UNKNOWN, null, null);

    public static ClientProfile unknown() {
        return UNKNOWN;
    }

    /**
     * Lit un {@code User-Agent}.
     *
     * <p>L'ordre des tests n'est pas indifférent, et c'est tout le piège de cet exercice : un iPad
     * se déclare « Macintosh » depuis iPadOS 13, Edge et Chrome se déclarent tous deux
     * « Chrome », et Chrome se déclare « Safari ». On va donc systématiquement du plus spécifique
     * au plus générique.</p>
     */
    public static ClientProfile of(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return UNKNOWN;
        }
        String ua = userAgent.toLowerCase(Locale.ROOT);
        return new ClientProfile(platformOf(ua), osOf(ua), browserOf(ua));
    }

    private static Platform platformOf(String ua) {
        // « ipad » d'abord : il contient aussi « mobile » sur certaines versions.
        if (ua.contains("ipad") || (ua.contains("android") && !ua.contains("mobile"))
                || ua.contains("tablet")) {
            return Platform.TABLET;
        }
        if (ua.contains("iphone") || ua.contains("ipod") || ua.contains("android")
                || ua.contains("mobile") || ua.contains("windows phone")) {
            return Platform.MOBILE;
        }
        if (ua.contains("macintosh") || ua.contains("windows") || ua.contains("linux")
                || ua.contains("cros")) {
            return Platform.DESKTOP;
        }
        return Platform.UNKNOWN;
    }

    private static String osOf(String ua) {
        if (ua.contains("iphone") || ua.contains("ipad") || ua.contains("ipod")) {
            return "iOS";
        }
        if (ua.contains("android")) {
            return "Android";
        }
        if (ua.contains("windows")) {
            return "Windows";
        }
        if (ua.contains("cros")) {
            return "ChromeOS";
        }
        if (ua.contains("macintosh") || ua.contains("mac os")) {
            return "macOS";
        }
        if (ua.contains("linux")) {
            return "Linux";
        }
        return null;
    }

    private static String browserOf(String ua) {
        if (ua.contains("edg/") || ua.contains("edgios")) {
            return "Edge";
        }
        if (ua.contains("opr/") || ua.contains("opera")) {
            return "Opera";
        }
        if (ua.contains("firefox/") || ua.contains("fxios")) {
            return "Firefox";
        }
        // Sur iOS, tous les navigateurs embarquent le moteur de Safari : « crios » est Chrome.
        if (ua.contains("crios")) {
            return "Chrome";
        }
        if (ua.contains("chrome/") || ua.contains("chromium")) {
            return "Chrome";
        }
        if (ua.contains("safari/")) {
            return "Safari";
        }
        return null;
    }
}
