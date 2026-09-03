package com.gsl.browser;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Classifies a CRITICAL BrowserLogRecord into a semantic root-cause category.
 * Only called for CRITICAL-level records; all others return UNKNOWN.
 * Pattern evaluation order matters — more specific patterns are checked first.
 */
public final class JsErrorClassificationEngine {

    private JsErrorClassificationEngine() {}

    private record Rule(List<String> patterns, JsErrorCategory category, String cause, String confidence) {}

    private static final List<Rule> RULES = List.of(
        // ExtJS component lifecycle — highest specificity
        new Rule(List.of(
                "reading 'el'", ".el is undefined", "component.el",
                "Ext.panel", "Ext.window", "Ext.grid", "Ext.container",
                "initComponent", "afterRender", "onRender",
                "is not a constructor", "destroyed", "this.el"),
            JsErrorCategory.EXTJS_COMPONENT_LIFECYCLE,
            "Destroyed or unrendered Ext.js component referenced",
            "HIGH"),

        // Store / grid data load
        new Rule(List.of(
                "store.load", "getStore", "Store is loading",
                "store.reload", "bufferedrenderer", "getAt("),
            JsErrorCategory.STORE_LOAD_FAILURE,
            "Grid or store not loaded when component was accessed",
            "HIGH"),

        // Network / API failure causing undefined data
        new Rule(List.of(
                "Failed to fetch", "net::ERR_", "NetworkError",
                "XMLHttpRequest", "fetch failed", "500 (Internal", "502", "503"),
            JsErrorCategory.NETWORK_RESPONSE_FAILURE,
            "API failure likely caused undefined data binding",
            "HIGH"),

        // Async callback after lifecycle end
        new Rule(List.of(
                "setTimeout", "setInterval", "clearTimeout",
                "callback", "Promise", "async", "await", "then("),
            JsErrorCategory.ASYNC_CALLBACK_FAILURE,
            "Callback or Promise resolved after component lifecycle ended",
            "MEDIUM"),

        // DOM reference missing
        new Rule(List.of(
                "getElementById", "querySelector", "null is not",
                "undefined is not", "Cannot set properties of null",
                "Cannot read properties of null"),
            JsErrorCategory.DOM_REFERENCE_FAILURE,
            "Missing or detached DOM node referenced",
            "MEDIUM"),

        // Race condition / timing
        new Rule(List.of(
                "race", "concurrent", "already in progress",
                "still loading", "not ready"),
            JsErrorCategory.RACE_CONDITION,
            "Probable timing or race condition between async operations",
            "MEDIUM")
    );

    /**
     * Classifies a single log record. Returns UNKNOWN when no rule matches.
     * Only meaningful for CRITICAL-level records.
     */
    public static JsErrorClassification classify(BrowserLogRecord record) {
        String combined = (record.message() + " " + record.stack()).toLowerCase();
        for (Rule rule : RULES) {
            if (rule.patterns().stream().anyMatch(p -> combined.contains(p.toLowerCase()))) {
                return new JsErrorClassification(rule.category(), rule.cause(), rule.confidence());
            }
        }
        return JsErrorClassification.unknown();
    }

    /**
     * Classifies the first CRITICAL record in the list; returns UNKNOWN if none.
     *
     * @deprecated Round 11 (§22): picking the FIRST critical error in a test's whole browser-log
     * history has no relationship to WHEN a failure actually happened — confirmed live (run
     * 20260903_173520) mis-attributing a `TimeoutException` to a JS error that last fired 68s
     * earlier, during an unrelated step. That exact stale error recurs in dozens of historical
     * runs (including passing tests) — it's normal background noise whenever it isn't actually
     * close to the failure in time. Prefer {@link #mostRecentWithin(List, long, long)}, which only
     * considers errors temporally correlated with the failure.
     */
    @Deprecated
    public static JsErrorClassification classifyFirst(List<BrowserLogRecord> logs) {
        return logs.stream()
                .filter(r -> r.level() == LogSeverity.CRITICAL)
                .findFirst()
                .map(JsErrorClassificationEngine::classify)
                .orElse(JsErrorClassification.unknown());
    }

    /**
     * Round 11 (§22): the most recent CRITICAL record whose {@code timestamp} falls within
     * {@code windowMs} of {@code nowMs} — {@code nowMs} should be the moment evidence was
     * collected (i.e. right after the failure). Returns empty when no CRITICAL record falls
     * inside the window, rather than falling back to an older, uncorrelated one — an app-side JS
     * error that fired long before the failure and isn't temporally connected to it should not be
     * treated as its cause.
     */
    public static Optional<BrowserLogRecord> mostRecentWithin(
            List<BrowserLogRecord> logs, long nowMs, long windowMs) {
        return logs.stream()
                .filter(r -> r.level() == LogSeverity.CRITICAL)
                .filter(r -> r.timestamp() > 0 && (nowMs - r.timestamp()) <= windowMs)
                .max(Comparator.comparingLong(BrowserLogRecord::timestamp));
    }
}
