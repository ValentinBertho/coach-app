package com.coachrun.security;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Convertisseur JPA chiffrant un entier au repos (FC max, FC repos…). */
@Component
@Converter
@RequiredArgsConstructor
public class EncryptedIntegerConverter implements AttributeConverter<Integer, String> {

    private final EncryptionService encryptionService;

    @Override
    public String convertToDatabaseColumn(Integer attribute) {
        return attribute == null ? null : encryptionService.encrypt(attribute.toString());
    }

    @Override
    public Integer convertToEntityAttribute(String dbData) {
        String value = encryptionService.decrypt(dbData);
        return value == null ? null : Integer.valueOf(value);
    }
}
