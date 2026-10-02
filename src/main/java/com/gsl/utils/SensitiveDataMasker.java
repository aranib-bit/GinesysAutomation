package com.gsl.utils;

import org.openqa.selenium.By;

import java.util.Arrays;
import java.util.List;

/**
 * Decides whether a value bound for a log/report line should be masked, and performs that
 * masking. Sensitivity is decided defensively, on any of three independent signals — an explicit
 * {@link ElementMeta#isSensitive()} flag, a sensitive keyword in the element's own name, or a
 * sensitive keyword in its locator string — so a caller only needs one of them to be right for a
 * value to get masked; it never relies solely on every caller remembering to tag
 * {@link ElementMeta#sensitive(String)} correctly.
 */
public class SensitiveDataMasker {

    private static final List<String> SENSITIVE_KEYWORDS = Arrays.asList(
            "password", "pwd", "pass", "pin", "otp", "secret", "token"
    );

    /** {@code true} if {@code elementName} itself contains a sensitive keyword (e.g. "password"). */
    public static boolean isSensitiveByName(String elementName) {
        return containsKeyword(elementName);
    }

    /**
     * {@code true} if {@code meta} is explicitly flagged sensitive, {@code meta}'s name contains a
     * sensitive keyword, or {@code locator}'s own string form contains one — any single signal is
     * enough; either argument may be {@code null}.
     */
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

    /**
     * Masks {@code value} for safe logging: {@code null} stays {@code null}; a value 2 characters
     * or shorter becomes {@code "**"} (too short to safely reveal a prefix); anything longer keeps
     * its first 2 characters and replaces the rest with a fixed {@code "****"}.
     */
    public static String mask(String value) {
        if (value == null) return null;
        if (value.length() <= 2) return "**";
        return value.substring(0, 2) + "****";
    }
}
