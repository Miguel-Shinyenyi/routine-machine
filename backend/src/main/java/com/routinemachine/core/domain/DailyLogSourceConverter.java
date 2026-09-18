package com.routinemachine.core.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class DailyLogSourceConverter implements AttributeConverter<DailyLogSource, String> {

    @Override
    public String convertToDatabaseColumn(DailyLogSource attribute) {
        return attribute == null ? null : attribute.getValue();
    }

    @Override
    public DailyLogSource convertToEntityAttribute(String dbData) {
        return dbData == null ? null : DailyLogSource.fromValue(dbData);
    }
}
