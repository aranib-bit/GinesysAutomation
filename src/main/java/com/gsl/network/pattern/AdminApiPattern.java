package com.gsl.network.pattern;

import java.util.List;

/** API URL patterns for the Admin module. Populate as endpoints are confirmed. */
public enum AdminApiPattern implements ApiPattern {

    PLACEHOLDER("Admin (stub)", List.of("/Admin/api/"));

    private final String       displayName;
    private final List<String> urlPatterns;

    AdminApiPattern(String displayName, List<String> urlPatterns) {
        this.displayName = displayName;
        this.urlPatterns = urlPatterns;
    }

    @Override public String       displayName() { return displayName; }
    @Override public List<String> urlPatterns() { return urlPatterns; }
}
