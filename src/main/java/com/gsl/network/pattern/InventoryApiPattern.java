package com.gsl.network.pattern;

import java.util.List;

/**
 * {@link ApiPattern} constants for the Inventory module. Populate as endpoints are confirmed —
 * see {@link FinanceApiPattern} for a worked example of confirmed, per-endpoint constants.
 */
public enum InventoryApiPattern implements ApiPattern {

    PLACEHOLDER("Inventory (stub)", List.of("/Inventory/api/"));

    private final String       displayName;
    private final List<String> urlPatterns;

    InventoryApiPattern(String displayName, List<String> urlPatterns) {
        this.displayName = displayName;
        this.urlPatterns = urlPatterns;
    }

    @Override public String       displayName() { return displayName; }
    @Override public List<String> urlPatterns() { return urlPatterns; }
}
