package com.coachrun.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Pièce jointe d'un message (image ou PDF), stockée en base (cf. DARI Lab — messagerie).
 * Les octets sont chargés à la demande (téléchargement) ; la métadonnée vit aussi sur le message.
 */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "message_attachments")
public class MessageAttachment extends BaseEntity {

    @Column(name = "filename", nullable = false)
    private String filename;

    @Column(name = "content_type", nullable = false, length = 128)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "data", nullable = false)
    private byte[] data;
}
