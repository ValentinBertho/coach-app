package com.coachrun.security;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/** Convertisseur JPA chiffrant un décimal au repos (VMA, poids…). */
@Component
@Converter
@RequiredArgsConstructor
public class EncryptedBigDecimalConverter implements AttributeConverter<BigDecimal, String> {

    private final EncryptionService encryptionService;

    @Override
    public String convertToDatabaseColumn(BigDecimal attribute) {
        return attribute == null ? null : encryptionService.encrypt(attribute.toPlainString());
    }

    @Override
    public BigDecimal convertToEntityAttribute(String dbData) {
        String value = encryptionService.decrypt(dbData);
        return value == null ? null : new BigDecimal(value);
    }
}
