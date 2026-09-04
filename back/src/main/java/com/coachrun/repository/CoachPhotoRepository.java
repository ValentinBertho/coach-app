package com.coachrun.repository;

import com.coachrun.entity.CoachPhoto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

public interface CoachPhotoRepository extends JpaRepository<CoachPhoto, UUID> {

    Optional<CoachPhoto> findByProfileId(UUID profileId);

    void deleteByProfileId(UUID profileId);

    /**
     * L'identifiant de la photo, sans ses octets.
     *
     * <p>C'est ce dont la fiche a besoin pour composer son URL. Charger l'entité entière aurait
     * ramené l'image en mémoire à chaque lecture de profil, pour n'en afficher qu'un lien.</p>
     */
    @Query("select p.id from CoachPhoto p where p.profile.id = :profileId")
    Optional<UUID> findIdByProfileId(@org.springframework.data.repository.query.Param("profileId") UUID profileId);
}
