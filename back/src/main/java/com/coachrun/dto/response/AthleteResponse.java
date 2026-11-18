package com.coachrun.dto.response;

import com.coachrun.entity.Athlete;
import com.coachrun.entity.enums.AthleteLevel;
import com.coachrun.entity.enums.AthleteStatus;
import com.coachrun.entity.enums.Sex;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** Détail complet d'un athlète (déchiffrement des données de santé à la lecture). */
public record AthleteResponse(
        UUID id,
        String firstName,
        String lastName,
        String email,
        LocalDate birthDate,
        Sex sex,
        AthleteLevel level,
        AthleteStatus status,
        Integer hrMax,
        Integer hrRest,
        BigDecimal vma,
        BigDecimal weightKg,
        String medicalNotes,
        /**
         * Le traitement des données de santé de cet athlète est-il autorisé en ce moment ?
         *
         * <p>Le consentement n'était exposé nulle part : le coach saisissait notes médicales,
         * tests de lactate et douleurs sans aucun moyen de savoir si l'athlète avait accepté son
         * invitation — et le parcours normal est justement de remplir la fiche <em>avant</em>
         * d'inviter. Il n'avait pas non plus de quoi comprendre un refus après retrait.</p>
         */
        boolean healthDataConsentGranted,
        java.time.Instant healthDataConsentAt,
        boolean invitationPending,
        java.util.UUID groupId,
        String groupName,
        List<RefResponse> coaches,
        List<RefResponse> clubs,
        boolean appAccount,
        /** Le suivi est terminé : la fiche reste en lecture chez le coach qui l'a tenue. */
        boolean coachingEnded) {

    /**
     * Sans savoir si la personne a un accès à l'application : on s'en tient au lien de compte du
     * hub. Les listes n'en ont pas besoin ; seule la fiche détaillée l'interroge vraiment.
     */
    public static AthleteResponse from(Athlete a) {
        return from(a, a.getAccount() != null);
    }

    /**
     * @param appAccount la personne se connecte-t-elle à l'application ? C'est ce qui décide si
     *                   « mettre fin au suivi » a un sens : un athlète saisi par son coach, sans
     *                   accès, n'a rien à quitter — pour lui, l'archivage suffit.
     */
    public static AthleteResponse from(Athlete a, boolean appAccount) {
        return from(a, appAccount, false);
    }

    public static AthleteResponse from(Athlete a, boolean appAccount, boolean coachingEnded) {
        // Coachs explicitement rattachés (modèle many-to-many).
        List<RefResponse> coaches = a.getCoaches().stream()
                .map(u -> new RefResponse(u.getId(), u.getFullName()))
                .sorted(Comparator.comparing(RefResponse::name, Comparator.nullsLast(String::compareTo)))
                .toList();
        // Clubs = club principal + clubs additionnels (modèle many-to-many).
        List<RefResponse> clubs = new ArrayList<>();
        clubs.add(new RefResponse(a.getClub().getId(), a.getClub().getName()));
        a.getAdditionalClubs().stream()
                .map(c -> new RefResponse(c.getId(), c.getName()))
                .sorted(Comparator.comparing(RefResponse::name, Comparator.nullsLast(String::compareTo)))
                .forEach(clubs::add);

        return new AthleteResponse(
                a.getId(), a.getFirstName(), a.getLastName(), a.getEmail(),
                a.getBirthDate(), a.getSex(), a.getLevel(), a.getStatus(),
                a.getHrMax(), a.getHrRest(), a.getVma(), a.getWeightKg(),
                a.getMedicalNotes(),
                a.isHealthDataConsentActive(),
                a.getHealthDataConsentAt(),
                a.getInviteToken() != null,
                a.getGroup() != null ? a.getGroup().getId() : null,
                a.getGroup() != null ? a.getGroup().getName() : null,
                coaches, clubs, appAccount, coachingEnded);
    }
}
