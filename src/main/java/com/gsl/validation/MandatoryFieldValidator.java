package com.gsl.validation;

import com.gsl.testdata.annotations.Mandatory;
import com.gsl.testdata.annotations.AtLeastOneMandatory;

import java.lang.reflect.Field;

public class MandatoryFieldValidator {

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
