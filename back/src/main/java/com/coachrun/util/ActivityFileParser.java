package com.coachrun.util;

/**
 * Porte d'entrée de l'import de fichier : choisit le décodeur d'après le <b>contenu</b>, jamais
 * d'après l'extension.
 *
 * <p>C'est délibéré. Un athlète qui exporte depuis Garmin Connect récupère un {@code .fit} ; le
 * même parcours passé par un convertisseur ressort en {@code .tcx} renommé {@code .fit}, et
 * l'inverse arrive tout autant. Se fier au nom, c'est refuser un fichier parfaitement lisible
 * pour une raison que l'utilisateur ne peut ni voir ni corriger.</p>
 */
public final class ActivityFileParser {

    /** Formats acceptés, tels qu'ils sont annoncés à l'utilisateur et dans l'attribut d'upload. */
    public static final String SUPPORTED_EXTENSIONS = ".fit, .gpx, .tcx";

    private ActivityFileParser() {
    }

    /**
     * Décode une trace importée.
     *
     * @param content contenu brut du fichier
     * @return l'activité décodée
     * @throws IllegalArgumentException si le format n'est reconnu ni comme FIT ni comme XML, ou
     *                                  si le fichier ne contient rien d'exploitable — le message
     *                                  est destiné à l'utilisateur final
     */
    public static ActivityTrack.ParsedActivity parse(byte[] content) {
        if (content == null || content.length == 0) {
            throw new IllegalArgumentException("Fichier vide.");
        }
        if (FitParser.looksLikeFit(content)) {
            return FitParser.parse(content);
        }
        if (looksLikeXml(content)) {
            return GpxParser.parse(content);
        }
        throw new IllegalArgumentException(
                "Format non reconnu. Formats acceptés : " + SUPPORTED_EXTENSIONS + ".");
    }

    /** Premier caractère non blanc à {@code <} : GPX comme TCX sont du XML, avec ou sans BOM. */
    private static boolean looksLikeXml(byte[] content) {
        int i = 0;
        if (content.length >= 3 && (content[0] & 0xFF) == 0xEF
                && (content[1] & 0xFF) == 0xBB && (content[2] & 0xFF) == 0xBF) {
            i = 3; // BOM UTF-8
        }
        for (; i < content.length && i < 64; i++) {
            char ch = (char) (content[i] & 0xFF);
            if (ch == '<') {
                return true;
            }
            if (!Character.isWhitespace(ch)) {
                return false;
            }
        }
        return false;
    }
}
