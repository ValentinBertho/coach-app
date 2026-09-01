package com.coachrun.entity.enums;

import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Comment on entre sur la plateforme.
 *
 * <p>Trois régimes, du plus ouvert au plus tenu :</p>
 * <ul>
 *   <li>{@link #OPEN} — n'importe qui crée son club en remplissant le formulaire. C'était le
 *       réglage historique, et le seul disponible : {@code /auth/register} était public et
 *       n'exigeait que l'unicité de l'adresse.</li>
 *   <li>{@link #INVITE} — un code partagé, distribué par e-mail à une cohorte fermée. Suffisant
 *       pour cinq coachs qu'on connaît ; un code partagé se transfère, se colle dans un message,
 *       et ne dit jamais qui l'a utilisé.</li>
 *   <li>{@link #REQUEST} — le candidat dépose une <b>demande</b> de création de club ;
 *       l'administrateur la valide ou la refuse depuis le back-office, et c'est la validation qui
 *       crée le club et le compte. C'est le régime d'une bêta ouverte : la porte reste visible et
 *       le formulaire ouvert à tous, mais rien n'est créé sans décision humaine.</li>
 * </ul>
 */
public enum RegistrationMode {

    OPEN,
    INVITE,
    REQUEST;

    /**
     * Lit la valeur d'une variable d'environnement, ou {@code null} si elle n'est reconnue.
     *
     * <p>{@code null} plutôt qu'un repli sur {@link #OPEN} : une faute de frappe ne doit pas
     * ouvrir la création de club à tout venant. Les appelants traitent l'inconnu explicitement —
     * le garde-fou de démarrage refuse de booter, ce qui est le bon moment pour l'apprendre.</p>
     */
    public static RegistrationMode parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String normalized = raw.trim().toUpperCase(Locale.ROOT);
        return Arrays.stream(values())
                .filter(m -> m.name().equals(normalized))
                .findFirst()
                .orElse(null);
    }

    /** Les valeurs acceptées, telles qu'on les écrit dans un message d'erreur. */
    public static String accepted() {
        return Arrays.stream(values())
                .map(m -> m.name().toLowerCase(Locale.ROOT))
                .collect(Collectors.joining(", "));
    }

    /** Libellé français, pour l'écran de configuration. */
    public String label() {
        return switch (this) {
            case OPEN -> "Libre";
            case INVITE -> "Sur code d'invitation";
            case REQUEST -> "Sur demande validée";
        };
    }
}
