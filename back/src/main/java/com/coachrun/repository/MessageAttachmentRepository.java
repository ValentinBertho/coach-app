package com.coachrun.repository;

import com.coachrun.entity.MessageAttachment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface MessageAttachmentRepository extends JpaRepository<MessageAttachment, UUID> {

    /** Octets déjà consommés par un club (quota de stockage). 0 si aucune pièce jointe. */
    @Query("select coalesce(sum(a.sizeBytes), 0) from MessageAttachment a where a.club.id = :clubId")
    long totalSizeBytesByClub(@Param("clubId") UUID clubId);
}
