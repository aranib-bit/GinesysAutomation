package com.gsl.reports.contract;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Static registry of {@link DomainReporter} implementations.
 *
 * <p>Each domain registers itself once in
 * {@link com.gsl.listeners.TestListener#onStart(org.testng.ISuite)}:
 * <pre>
 *   DomainReporterRegistry.register(new FinanceDomainReporter());
 * </pre>
 *
 * <p>At test time, {@link com.gsl.listeners.TestListener} calls
 * {@link #resolve(Object[])} to obtain the matching reporter without
 * importing any domain-specific DTO class.
 *
 * <p>Thread-safe: uses a {@link CopyOnWriteArrayList} so suite-start
 * registration (single writer) and per-test reads (many concurrent threads)
 * do not contend.
 */
public final class DomainReporterRegistry {

    private static final List<DomainReporter> REPORTERS = new CopyOnWriteArrayList<>();

    private DomainReporterRegistry() {}

    /** Registers a reporter. Call once per domain in {@code TestListener.onStart(ISuite)}. */
    public static void register(DomainReporter reporter) {
        REPORTERS.add(reporter);
    }

    /**
     * Returns the first registered reporter that supports the given test parameters,
     * or {@link Optional#empty()} when no domain owns these params.
     */
    public static Optional<DomainReporter> resolve(Object[] params) {
        return REPORTERS.stream().filter(r -> r.supports(params)).findFirst();
    }
}
