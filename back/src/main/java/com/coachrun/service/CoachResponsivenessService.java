package com.coachrun.service;

import com.coachrun.entity.CoachProfile;
import com.coachrun.entity.CoachingRequest;
import com.coachrun.repository.CoachProfileRepository;
import com.coachrun.repository.CoachingRequestRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * La réactivité d'un coach, mesurée plutôt que déclarée.
 *
 * <h2>Pourquoi ce signal existe</h2>
 *
 * <p>Il n'y a pas d'avis au lancement, et c'était une décision : avec quelques dizaines de coachs,
 * une note est statistiquement muette et socialement violente. Restait à donner à l'athlète
 * <b>quelque chose</b> pour choisir. Le délai de réponse a trois qualités qu'une note n'a pas : il
 * est factuel, il n'est pas manipulable, et il dit ce qui compte réellement au moment de solliciter
 * quelqu'un — « est-ce que cette personne va me répondre ? ».</p>
 *
 * <h2>Ce qui est compté, et ce qui ne l'est pas</h2>
 *
 * <p>Seules les demandes <b>tranchées</b> — acceptées ou refusées — alimentent le délai. Un retrait
 * par l'athlète ne dit rien du coach ; une expiration est justement l'absence de réponse, et la
 * compter comme un délai très long ferait passer un silence pour une lenteur. Elle pèse ailleurs,
 * dans le <em>taux</em> de réponse.</p>
 *
 * <p><b>La médiane, pas la moyenne.</b> Un coach qui répond en deux heures et part trois semaines en
 * stage a une moyenne catastrophique et une médiane juste. C'est la médiane qui décrit ce à quoi
 * un athlète doit s'attendre.</p>
 *
 * <p><b>Rien n'est publié en dessous de {@link #MIN_SAMPLE} demandes.</b> Un coach qui a répondu
 * une fois en dix minutes n'est pas « très réactif » : il a eu une demande. Afficher un chiffre sur
 * un échantillon d'un serait une promesse que rien ne fonde.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CoachResponsivenessService {

    /** En dessous, aucun chiffre n'est publié : un échantillon d'un ne décrit rien. */
    public static final int MIN_SAMPLE = 3;

    private final CoachingRequestRepository requestRepository;
    private final CoachProfileRepository profileRepository;

    /**
     * Recalcule le délai médian de tous les coachs ayant reçu des demandes.
     *
     * <p>Un balayage complet plutôt qu'un calcul à chaque décision : c'est une donnée d'affichage,
     * pas une donnée de décision, et la recalculer en pleine transaction d'acceptation ferait payer
     * au geste le plus important du produit le coût d'une statistique.</p>
     *
     * @return le nombre de fiches mises à jour
     */
    @Transactional
    public int refreshAll() {
        int updated = 0;
        for (UUID coachId : requestRepository.findCoachIdsWithRequests()) {
            CoachProfile profile = profileRepository.findByCoachId(coachId).orElse(null);
            if (profile == null) {
                continue;   // un coach peut recevoir des demandes puis dépublier sa fiche
            }
            Integer median = medianResponseHours(coachId);
            if (!java.util.Objects.equals(profile.getMedianResponseHours(), median)) {
                profile.setMedianResponseHours(median);
                updated++;
            }
        }
        if (updated > 0) {
            log.info("Réactivité des coachs : {} fiche(s) mise(s) à jour", updated);
        }
        return updated;
    }

    /**
     * Le délai médian de réponse en heures, ou {@code null} si l'échantillon est trop mince.
     *
     * <p>Arrondi à l'heure supérieure, et jamais à zéro : « répond en 0 h » ne veut rien dire, et
     * une réponse en dix minutes se dit « moins d'une heure ».</p>
     */
    public Integer medianResponseHours(UUID coachId) {
        List<CoachingRequest> decided = requestRepository.findDecidedByCoach(coachId);
        if (decided.size() < MIN_SAMPLE) {
            return null;
        }
        List<Long> hours = new ArrayList<>();
        for (CoachingRequest r : decided) {
            if (r.getCreatedAt() == null || r.getDecidedAt() == null) {
                continue;
            }
            long minutes = Duration.between(r.getCreatedAt(), r.getDecidedAt()).toMinutes();
            hours.add(Math.max(1, (long) Math.ceil(minutes / 60.0)));
        }
        if (hours.size() < MIN_SAMPLE) {
            return null;
        }
        hours.sort(Long::compareTo);
        int mid = hours.size() / 2;
        long median = hours.size() % 2 == 1
                ? hours.get(mid)
                : Math.round((hours.get(mid - 1) + hours.get(mid)) / 2.0);
        return (int) Math.min(median, Integer.MAX_VALUE);
    }

    /**
     * La part des demandes auxquelles ce coach a répondu, en pourcentage.
     *
     * <p>C'est ici que pèse le silence : une demande expirée compte au dénominateur et pas au
     * numérateur. Un coach qui répond vite mais laisse filer une demande sur deux le montre.</p>
     *
     * <p>Un <b>retrait</b> par l'athlète, lui, ne compte nulle part : rien ne dit que le coach
     * n'allait pas répondre, et le faire baisser pour un geste qui n'est pas le sien serait une
     * accusation gratuite sur une page publique.</p>
     *
     * @return {@code null} tant que l'échantillon est trop mince
     */
    public Integer responseRatePercent(UUID coachId) {
        long answerable = requestRepository.countAnswerableByCoach(coachId);
        if (answerable < MIN_SAMPLE) {
            return null;
        }
        long answered = requestRepository.findDecidedByCoach(coachId).size();
        return (int) Math.round(100.0 * answered / answerable);
    }
}
