package com.coachrun.dto.response;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Comment cette personne se sert de l'application.
 *
 * <h2>La question à laquelle ce bloc répond</h2>
 *
 * <p>La fiche disait <b>qui</b> était le compte et <b>ce qu'on lui avait fait</b>. Elle ne disait
 * rien de son usage : sur quoi elle travaille, si sa montre est branchée, si les notifications
 * peuvent seulement l'atteindre. Ces réponses se cherchaient en base, une par une — ou, plus
 * souvent, se devinaient. Elles décident pourtant de ce qu'on répond à un ticket : conseiller
 * d'installer l'application à quelqu'un qui n'a pas de push, ou chercher un bug d'import chez
 * quelqu'un dont Strava n'a jamais été connecté, fait perdre le même temps aux deux côtés.</p>
 *
 * <p><b>Aucune donnée de santé.</b> On compte des séances et des sorties ; on ne rend ni RPE, ni
 * douleur, ni fatigue, ni commentaire. Ces valeurs ne se consultent que depuis l'écran athlète,
 * qui porte ses propres gardes.</p>
 */
public record AdminUserUsageResponse(
        ClientInfo client,
        NotificationsInfo notifications,
        WatchInfo watch,
        EngagementInfo engagement) {

    /**
     * Avec quoi elle vient.
     *
     * @param platform          Mobile / Tablette / Ordinateur / Inconnu, dérivé du dernier agent
     * @param appVersion        version du front qu'elle faisait tourner à sa dernière visite
     * @param latestAppVersion  version servie par ce serveur, pour la comparaison
     * @param outdated          vrai quand les deux diffèrent — un service worker qui n'a pas repris
     * @param userAgent         la chaîne brute, pour les cas que le classement ne couvre pas
     */
    public record ClientInfo(
            String platform,
            String os,
            String browser,
            String appVersion,
            String latestAppVersion,
            boolean outdated,
            String userAgent) {
    }

    /**
     * Ce qui peut l'atteindre.
     *
     * <p>{@code reachable} est la seule question qui compte, et elle ne se déduit d'aucun des
     * autres champs pris isolément : il faut un appareil abonné <b>et</b> la préférence active.
     * C'est exactement le cas qui a coûté trois jours en bêta — un athlète sans push, une question
     * du coach qui n'annonçait rien, et personne pour le voir.</p>
     */
    public record NotificationsInfo(
            boolean pushEnabled,
            boolean emailEnabled,
            boolean emailVerified,
            List<String> mutedCategories,
            long devices,
            boolean reachable,
            Instant lastPushSuccessAt,
            List<DeviceInfo> deviceList) {
    }

    /** Un appareil abonné, tel qu'il s'est déclaré à l'abonnement. */
    public record DeviceInfo(
            String platform,
            String os,
            String browser,
            Instant since,
            Instant lastSuccessAt) {
    }

    /**
     * La montre.
     *
     * @param canRenameOnProvider le jeton porte-t-il {@code activity:write} ? Distinct du
     *                            consentement : cocher la case dans Darilab ne vaut pas
     *                            autorisation chez Strava, et c'est la confusion la plus fréquente
     * @param renameOptIn         la case, elle, telle que l'athlète l'a laissée
     */
    public record WatchInfo(
            boolean connected,
            String provider,
            Instant connectedAt,
            Instant lastImportAt,
            boolean canRenameOnProvider,
            boolean renameOptIn) {
    }

    /**
     * Des signes de vie, pas un score.
     *
     * <p>Volontairement peu de chiffres et aucun jugement : l'objet est de distinguer un compte
     * qui vit d'un compte abandonné avant de décider quoi que ce soit à son sujet. Les champs
     * propres à l'athlète sont nuls pour un coach — il n'a ni séances ni sorties.</p>
     */
    public record EngagementInfo(
            Instant lastLoginAt,
            Instant lastSeenAt,
            Long sessionsCompleted30d,
            Long activitiesImported30d,
            LocalDate lastFeedbackAt) {
    }
}
