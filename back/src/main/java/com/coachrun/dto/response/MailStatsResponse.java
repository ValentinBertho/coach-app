package com.coachrun.dto.response;

import java.time.LocalDate;
import java.util.List;

/**
 * Consommation d'e-mails, telle que la lit l'administrateur : « où j'en suis de mon plafond »,
 * « qu'est-ce qui le consomme », « est-ce que quelque chose échoue ».
 */
public record MailStatsResponse(
        /** Envoyés depuis minuit, dans le fuseau de l'application. */
        long today,
        /** Plafond quotidien du plan d'envoi (0 = non renseigné, donc non affiché). */
        long dailyQuota,
        /** Envoyés depuis le 1er du mois. */
        long month,
        /** Plafond mensuel du plan d'envoi. */
        long monthlyQuota,
        /** Échecs des sept derniers jours : un pic précède toujours un « je n'ai rien reçu ». */
        long failed7d,
        /** Volume jour par jour sur la fenêtre demandée, du plus ancien au plus récent. */
        List<MailDayResponse> byDay,
        /** Répartition par nature d'envoi sur la fenêtre. */
        List<MailKindResponse> byKind
) {

    /** Un jour de l'histogramme. Les jours sans envoi valent zéro : une courbe ne saute pas de jours. */
    public record MailDayResponse(LocalDate date, long sent, long failed) { }

    /** Une nature d'envoi et son volume. {@code transactional} dit ce qu'on ne peut pas couper. */
    public record MailKindResponse(String kind, String label, boolean transactional, long count) { }
}
