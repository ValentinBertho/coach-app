package com.coachrun.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * La photo d'une fiche coach.
 *
 * <h2>Pourquoi une table à part</h2>
 *
 * <p>Les octets ne doivent pas voyager avec la fiche. L'annuaire lira bientôt vingt profils d'un
 * coup ; une colonne binaire sur {@code coach_profiles} les aurait chargés vingt fois pour n'en
 * afficher aucun, la liste ne servant que des vignettes par URL.</p>
 *
 * <h2>Ce que l'image stockée n'est plus</h2>
 *
 * <p>Elle n'est jamais celle qui a été envoyée. {@code CoachPhotoService} la décode, la redimensionne
 * et la ré-encode en JPEG. Trois conséquences voulues : les <b>métadonnées EXIF disparaissent</b> —
 * une photo de téléphone porte les coordonnées GPS de l'endroit où elle a été prise, souvent le
 * domicile ; un fichier qui prétend être une image sans en être une ne survit pas au décodage ; et
 * le poids devient prévisible quel que soit l'appareil.</p>
 */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "coach_photos")
public class CoachPhoto extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "coach_profile_id", nullable = false, unique = true)
    private CoachProfile profile;

    /** Toujours {@code image/jpeg} : c'est le format de sortie du ré-encodage. */
    @Column(name = "content_type", nullable = false, length = 64)
    private String contentType = "image/jpeg";

    @Column(name = "width", nullable = false)
    private int width;

    @Column(name = "height", nullable = false)
    private int height;

    @Column(name = "data", nullable = false)
    private byte[] data;
}
