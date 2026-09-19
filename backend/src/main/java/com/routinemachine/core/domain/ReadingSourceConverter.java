package com.routinemachine.core.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class ReadingSourceConverter implements AttributeConverter<ReadingSource, String> {

    @Override
    public String convertToDatabaseColumn(ReadingSource attribute) {
        return attribute == null ? null : attribute.getValue();
    }

    @Override
    public ReadingSource convertToEntityAttribute(String dbData) {
        return dbData == null ? null : ReadingSource.fromValue(dbData);
    }
}
