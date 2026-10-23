package com.api.fespot.global.aop;

import jakarta.persistence.*;
import org.hibernate.annotations.NaturalId;

import java.lang.reflect.Field;
import java.util.Arrays;

final class UniqueConstraintScanner {

    static boolean hasUnique(Class<?> type) {
        // @MappedSuperclass에서 상속받은 필드까지 확인
        for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
            Table table = c.getAnnotation(Table.class);
            if (table != null && (table.uniqueConstraints().length > 0
                    || Arrays.stream(table.indexes()).anyMatch(Index::unique))) {
                return true;
            }
            for (Field f : c.getDeclaredFields()) {
                if (isUniqueField(f)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isUniqueField(Field f) {
        Column column = f.getAnnotation(Column.class);
        JoinColumn joinColumn = f.getAnnotation(JoinColumn.class);
        if ((column != null && column.unique())
                || (joinColumn != null && joinColumn.unique())
                || f.isAnnotationPresent(NaturalId.class)) {
            return true;
        }
        // @Embedded 값 객체 내부의 @Column(unique = true)
        return f.getType().isAnnotationPresent(Embeddable.class) && hasUnique(f.getType());
    }
}