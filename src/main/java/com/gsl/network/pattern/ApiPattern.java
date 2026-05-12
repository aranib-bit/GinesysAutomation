package com.gsl.network.pattern;

import com.gsl.config.ConfigFactory;
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
        String base = stripTrailingSlash(ConfigFactory.getConfig().url());
        return all.stream()
                .filter(r -> urlPatterns().stream()
                        .map(p -> base + p)
                        .anyMatch(r.url()::startsWith))
                .toList();
    }

    private static String stripTrailingSlash(String url) {
        return (url != null && url.endsWith("/")) ? url.substring(0, url.length() - 1) : url;
    }
}
