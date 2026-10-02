package com.gsl.reports;

import com.gsl.reports.profile.RuntimeExecutionProfile;

/**
 * Suite-wide (not thread-local) holder for the one {@link RuntimeExecutionProfile} built for the
 * current run — set once by {@code TestListener} near suite start (via {@code RuntimeProfileBuilder
 * .build()}) and read later by any reporting code (e.g. {@code FailureSummaryRenderer}) that needs
 * the run's config without threading it through every call. Backed by a {@code volatile} field
 * rather than a {@code ThreadLocal} since the same profile applies to every thread in the run.
 */
public final class RuntimeProfileHolder {

    private static volatile RuntimeExecutionProfile profile;

    private RuntimeProfileHolder() {
    }

    /** Sets the current run's profile — called once, near suite start. */
    public static void set(RuntimeExecutionProfile p) {
        profile = p;
    }

    /** The current run's profile, or {@code null} if {@link #set} hasn't been called yet. */
    public static RuntimeExecutionProfile get() {
        return profile;
    }
}
