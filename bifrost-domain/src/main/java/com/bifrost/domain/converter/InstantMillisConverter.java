package com.bifrost.domain.converter;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.time.Instant;

/**
 * Instant ↔ 毫秒 INTEGER 转换器（全局自动应用）。
 *
 * <p>SQLite 无原生时间类型，统一以毫秒 INTEGER 存储便于排序，见《音乐管理技术设计》§2.2。</p>
 */
@Converter(autoApply = true)
public class InstantMillisConverter implements AttributeConverter<Instant, Long> {

    @Override
    public Long convertToDatabaseColumn(Instant attribute) {
        return attribute == null ? null : attribute.toEpochMilli();
    }

    @Override
    public Instant convertToEntityAttribute(Long dbData) {
        return dbData == null ? null : Instant.ofEpochMilli(dbData);
    }
}
