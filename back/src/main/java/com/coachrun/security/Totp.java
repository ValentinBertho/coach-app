package com.coachrun.security;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Locale;

/**
 * Codes à usage unique basés sur le temps (RFC 6238 : HMAC-SHA1, 6 chiffres, pas de 30 s) —
 * le format que lisent toutes les applications d'authentification (Google Authenticator,
 * 1Password, Authy, Bitwarden…).
 *
 * <p>Écrit ici plutôt qu'emprunté à une bibliothèque : l'algorithme tient en vingt lignes, est
 * vérifié contre les vecteurs de test de la RFC, et n'ajoute aucune dépendance à surveiller.</p>
 */
public final class Totp {

    public static final int PERIOD_SECONDS = 30;
    public static final int DIGITS = 6;
    /** Un pas avant et un après : tolère une horloge de téléphone décalée d'une trentaine de secondes. */
    public static final int WINDOW = 1;

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String BASE32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";

    private Totp() {
    }

    /** Nouveau secret de 160 bits, en base32 (ce que l'utilisateur saisit ou que le QR code contient). */
    public static String newSecret() {
        byte[] bytes = new byte[20];
        RANDOM.nextBytes(bytes);
        return base32(bytes);
    }

    public static long stepAt(long epochSeconds) {
        return Math.floorDiv(epochSeconds, PERIOD_SECONDS);
    }

    /** Code attendu pour un pas donné. */
    public static String code(String base32Secret, long step) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(unbase32(base32Secret), "HmacSHA1"));
            byte[] h = mac.doFinal(ByteBuffer.allocate(8).putLong(step).array());
            int offset = h[h.length - 1] & 0x0f;
            int binary = ((h[offset] & 0x7f) << 24) | ((h[offset + 1] & 0xff) << 16)
                    | ((h[offset + 2] & 0xff) << 8) | (h[offset + 3] & 0xff);
            return String.format("%06d", binary % 1_000_000);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA1 indisponible", e);
        }
    }

    /**
     * Pas auquel correspond le code présenté, dans la fenêtre autour de {@code nowStep} et
     * strictement après {@code lastAcceptedStep} ; {@code -1} sinon.
     */
    public static long match(String base32Secret, String presented, long nowStep, long lastAcceptedStep) {
        if (presented == null || !presented.matches("\\d{" + DIGITS + "}")) {
            return -1;
        }
        for (long step = nowStep - WINDOW; step <= nowStep + WINDOW; step++) {
            if (step > lastAcceptedStep && constantTimeEquals(code(base32Secret, step), presented)) {
                return step;
            }
        }
        return -1;
    }

    /** Lien {@code otpauth://} : c'est lui que le QR code encode. */
    public static String otpauthUri(String issuer, String account, String base32Secret) {
        String label = enc(issuer) + ":" + enc(account);
        return "otpauth://totp/" + label + "?secret=" + base32Secret + "&issuer=" + enc(issuer)
                + "&algorithm=SHA1&digits=" + DIGITS + "&period=" + PERIOD_SECONDS;
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static boolean constantTimeEquals(String a, String b) {
        return java.security.MessageDigest.isEqual(
                a.getBytes(StandardCharsets.US_ASCII), b.getBytes(StandardCharsets.US_ASCII));
    }

    static String base32(byte[] data) {
        StringBuilder out = new StringBuilder();
        int buffer = 0;
        int bits = 0;
        for (byte b : data) {
            buffer = (buffer << 8) | (b & 0xff);
            bits += 8;
            while (bits >= 5) {
                out.append(BASE32.charAt((buffer >> (bits - 5)) & 31));
                bits -= 5;
            }
        }
        if (bits > 0) {
            out.append(BASE32.charAt((buffer << (5 - bits)) & 31));
        }
        return out.toString();
    }

    static byte[] unbase32(String s) {
        String clean = s.replace("=", "").replace(" ", "").toUpperCase(Locale.ROOT);
        ByteBuffer out = ByteBuffer.allocate(clean.length() * 5 / 8);
        int buffer = 0;
        int bits = 0;
        for (char c : clean.toCharArray()) {
            int v = BASE32.indexOf(c);
            if (v < 0) {
                throw new IllegalArgumentException("Secret base32 invalide");
            }
            buffer = (buffer << 5) | v;
            bits += 5;
            if (bits >= 8) {
                out.put((byte) (buffer >> (bits - 8)));
                bits -= 8;
            }
        }
        return out.array();
    }
}
