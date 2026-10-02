package com.gsl.network.pattern;

import com.gsl.network.NetworkLogRecord;

import java.util.List;

/**
 * Names a module/feature's set of API URL patterns so captured {@link NetworkLogRecord}s can be
 * grouped and displayed per business area in reports, instead of as one undifferentiated list.
 * Implemented as an enum per module — see {@link AdminApiPattern}, {@link FinanceApiPattern},
 * {@link InventoryApiPattern} — with each constant naming one confirmed (or stubbed) endpoint group.
 */
public interface ApiPattern {

    /** Human-readable label for this pattern, used as a report grouping heading. */
    String displayName();

    /** Relative URL fragments (matched via {@link String#contains}) identifying this pattern's requests. */
    List<String> urlPatterns();

    /**
     * Filters network log records down to those whose URL contains at least one of this
     * pattern's {@link #urlPatterns()} fragments.
     */
    default List<NetworkLogRecord> filterLogs(List<NetworkLogRecord> all) {
        return all.stream()
                .filter(r -> urlPatterns().stream().anyMatch(p -> r.url().contains(p)))
                .toList();
    }
}
