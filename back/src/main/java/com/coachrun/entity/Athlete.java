package com.coachrun.entity;

import com.coachrun.entity.enums.AthleteLevel;
import com.coachrun.entity.enums.AthleteStatus;
import com.coachrun.entity.enums.Sex;
import com.coachrun.security.EncryptedBigDecimalConverter;
import com.coachrun.security.EncryptedIntegerConverter;
import com.coachrun.security.EncryptedStringConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Athlète suivi par un coach, rattaché à un club (tenant). Les données physiologiques
 * et médicales sont chiffrées au repos (RGPD art. 9).
 */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "athletes")
public class Athlete extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "club_id", nullable = false)
    private Club club;

    @Column(name = "first_name", nullable = false)
    private String firstName;

    @Column(name = "last_name", nullable = false)
    private String lastName;

    @Column(name = "email")
    private String email;

    @Column(name = "birth_date")
    private LocalDate birthDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "sex", length = 16)
    private Sex sex;

    @Enumerated(EnumType.STRING)
    @Column(name = "level", length = 16)
    private AthleteLevel level;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private AthleteStatus status = AthleteStatus.ACTIVE;

    // --- Données physiologiques (chiffrées au repos) ---
    @Convert(converter = EncryptedIntegerConverter.class)
    @Column(name = "hr_max")
    private Integer hrMax;

    @Convert(converter = EncryptedIntegerConverter.class)
    @Column(name = "hr_rest")
    private Integer hrRest;

    @Convert(converter = EncryptedBigDecimalConverter.class)
    @Column(name = "vma")
    private BigDecimal vma;

    @Convert(converter = EncryptedBigDecimalConverter.class)
    @Column(name = "weight_kg")
    private BigDecimal weightKg;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "medical_notes", length = 2048)
    private String medicalNotes;

    // --- Invitation par lien magique ---
    @Column(name = "invite_token", length = 64)
    private String inviteToken;

    @Column(name = "invite_expires_at")
    private Instant inviteExpiresAt;
}
