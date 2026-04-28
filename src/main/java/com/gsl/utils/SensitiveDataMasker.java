package com.gsl.utils;

import org.openqa.selenium.By;

import java.util.Arrays;
import java.util.List;

public class SensitiveDataMasker {

    private static final List<String> SENSITIVE_KEYWORDS = Arrays.asList(
            "password", "pwd", "pass", "pin", "otp", "secret", "token"
    );

    public static boolean isSensitive(ElementMeta meta, By locator) {
        if (meta != null && meta.isSensitive())                     return true;
        if (meta != null && containsKeyword(meta.getName()))        return true;
        if (locator != null && containsKeyword(locator.toString())) return true;
        return false;
    }

    private static boolean containsKeyword(String text) {
        if (text == null) return false;
        String lower = text.toLowerCase();
        return SENSITIVE_KEYWORDS.stream().anyMatch(lower::contains);
    }

    public static String mask(String value) {
        if (value == null) return null;
        if (value.length() <= 2) return "**";
        return value.substring(0, 2) + "****";
    }
}
