package com.gsl.network.pattern;

import com.gsl.network.NetworkLogRecord;

import java.util.List;

public interface ApiPattern {

    String displayName();
    List<String> urlPatterns();

    /**
     * Filters network log records to only those whose URL starts with
     * baseUrl + one of the relative urlPatterns().
     * Base URL is resolved from config so the same enum works across environments.
     */
    default List<NetworkLogRecord> filterLogs(List<NetworkLogRecord> all) {
        return all.stream()
                .filter(r -> urlPatterns().stream().anyMatch(p -> r.url().contains(p)))
                .toList();
    }
}
