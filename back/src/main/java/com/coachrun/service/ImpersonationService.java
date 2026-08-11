package com.coachrun.service;

import com.coachrun.dto.response.ImpersonationResponse;
import com.coachrun.dto.response.UserResponse;
import com.coachrun.entity.User;
import com.coachrun.entity.enums.UserRole;
import com.coachrun.exception.ForbiddenException;
import com.coachrun.exception.NotFoundException;
import com.coachrun.repository.UserRepository;
import com.coachrun.security.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Impersonation : un administrateur de plateforme obtient une session au nom d'un utilisateur, pour
 * voir l'application exactement comme lui.
 *
 * <p><b>Pourquoi c'est nécessaire.</b> La quasi-totalité des défauts remontés en bêta se décrivent
 * par un écran : « ma séance affiche 0,1 km », « je reçois repos demain alors que j'ai une
 * séance ». Les reproduire depuis un compte d'administration est impossible — les écrans
 * dépendent du programme, des allures et de l'historique du compte concerné. Sans ce mécanisme, la
 * seule alternative est de demander son mot de passe à l'utilisateur, ce qui est pire à tout point
 * de vue.</p>
 *
 * <p><b>Ce qui borne le pouvoir.</b> Quatre gardes, et aucune n'est cosmétique :</p>
 * <ul>
 *   <li>réservé à {@link UserRole#PLATFORM_ADMIN} (porté par l'annotation du contrôleur) ;</li>
 *   <li><b>un administrateur ne peut pas en emprunter un autre</b> : l'impersonation sert à voir
 *       ce que voit un coach ou un athlète, pas à agir sous l'identité d'un pair — et un journal
 *       où deux administrateurs s'empruntent mutuellement ne se lit plus ;</li>
 *   <li>pas de jeton de rafraîchissement : la session dure le temps d'un jeton d'accès ;</li>
 *   <li>chaque ouverture est <b>journalisée</b> avec les deux identités, en {@code WARN} — c'est
 *       un accès exceptionnel, il doit ressortir d'un journal qu'on parcourt.</li>
 * </ul>
 *
 * <p><b>Ce que ça ne fait pas.</b> Les actions effectuées pendant l'impersonation ne sont pas
 * attribuées à l'administrateur : elles ressemblent en tout point à celles de l'utilisateur. Le
 * jeton porte bien le claim {@code imp}, mais rien ne l'inscrit sur les données écrites. Tant que
 * ce n'est pas fait, l'impersonation est un outil de <b>lecture</b> : écrire depuis un compte
 * emprunté laisse une trace indiscernable de celle de son titulaire.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ImpersonationService {

    private final UserRepository userRepository;
    private final JwtService jwtService;

    /**
     * Ouvre une session au nom de {@code targetUserId}.
     *
     * @param adminUserId administrateur à l'origine de la demande
     * @throws ForbiddenException si la cible est un administrateur, ou l'administrateur lui-même
     */
    public ImpersonationResponse impersonate(UUID adminUserId, UUID targetUserId) {
        if (adminUserId.equals(targetUserId)) {
            throw new ForbiddenException("Vous êtes déjà connecté avec ce compte.");
        }
        User target = userRepository.findById(targetUserId)
                .orElseThrow(() -> new NotFoundException("Utilisateur introuvable."));
        if (target.getRole() == UserRole.PLATFORM_ADMIN) {
            throw new ForbiddenException(
                    "Un compte d'administration ne peut pas être emprunté : l'impersonation sert à "
                            + "voir l'application comme un coach ou un athlète.");
        }

        log.warn("[IMPERSONATION] admin={} ouvre une session au nom de user={} (rôle={})",
                adminUserId, targetUserId, target.getRole());

        return new ImpersonationResponse(
                jwtService.generateImpersonationToken(target, adminUserId),
                jwtService.getAccessTtlSeconds(),
                UserResponse.from(target));
    }
}
