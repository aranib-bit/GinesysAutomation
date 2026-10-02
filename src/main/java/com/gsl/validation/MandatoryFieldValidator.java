package com.gsl.validation;

import com.gsl.testdata.annotations.Mandatory;
import com.gsl.testdata.annotations.AtLeastOneMandatory;

import java.lang.reflect.Field;

/**
 * Reflection-driven enforcement of {@link Mandatory}/{@link AtLeastOneMandatory} on a test-data
 * DTO: field-level {@code @Mandatory} requires that field to be non-null/non-blank, and
 * type-level {@code @AtLeastOneMandatory} requires at least one field from its named group to be
 * populated. Call this once, after a DTO is fully assembled (Excel-merged or builder-constructed),
 * to fail fast with a clear message instead of letting a missing field surface as a confusing
 * downstream NPE or UI/DB mismatch.
 */
public class MandatoryFieldValidator {

    /**
     * Validates every {@code @Mandatory}-annotated field and, if present, the class-level
     * {@code @AtLeastOneMandatory} group on {@code data}. No-op when {@code data} is null.
     *
     * @throws IllegalStateException if a {@code @Mandatory} field is null/blank, or no field in
     *                                an {@code @AtLeastOneMandatory} group is populated
     */
    public static void validate(Object data) {

        if (data == null) {
            return;
        }

        Class<?> clazz = data.getClass();

        /* ---------- 1️⃣ FIELD-LEVEL @Mandatory ---------- */
        for (Field field : clazz.getDeclaredFields()) {

            if (field.isAnnotationPresent(Mandatory.class)) {
                field.setAccessible(true);
                try {
                    Object value = field.get(data);

                    if (value == null ||
                            (value instanceof String && ((String) value).isBlank())) {

                        Mandatory m = field.getAnnotation(Mandatory.class);
                        throw new IllegalStateException(
                                clazz.getSimpleName() + "." + field.getName()
                                        + " : " + m.message()
                        );
                    }
                } catch (IllegalAccessException e) {
                    throw new RuntimeException(e);
                }
            }
        }

        /* ---------- 2️⃣ TYPE-LEVEL @AtLeastOneMandatory ---------- */
        if (clazz.isAnnotationPresent(AtLeastOneMandatory.class)) {
            validateAtLeastOneMandatory(data, clazz);
        }
    }

    /* ---------- GROUP VALIDATION ---------- */
    /** Fails if none of {@code @AtLeastOneMandatory}'s named fields on {@code data} is populated. */
    private static void validateAtLeastOneMandatory(Object data, Class<?> clazz) {

        AtLeastOneMandatory rule =
                clazz.getAnnotation(AtLeastOneMandatory.class);

        boolean anyPresent = false;

        for (String fieldName : rule.fields()) {
            try {
                Field field = clazz.getDeclaredField(fieldName);
                field.setAccessible(true);
                Object value = field.get(data);

                if (value != null &&
                        !(value instanceof String && ((String) value).isBlank())) {
                    anyPresent = true;
                    break;
                }

            } catch (NoSuchFieldException e) {
                throw new RuntimeException(
                        "Invalid field name in @AtLeastOneMandatory: " + fieldName, e
                );
            } catch (IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        }

        if (!anyPresent) {
            throw new IllegalStateException(
                    clazz.getSimpleName() + " : " + rule.message()
            );
        }
    }
}
