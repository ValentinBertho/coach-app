package com.coachrun.util;

import java.text.Normalizer;
import java.util.Locale;

/** Génère des slugs URL-safe à partir d'un libellé (clubs, etc.). */
public final class SlugUtil {

    private SlugUtil() {
    }

    public static String slugify(String input) {
        String normalized = Normalizer.normalize(input, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
        return normalized.isBlank() ? "club" : normalized;
    }
}
