package com.gsl.approval.model;

/** Outcome of one approval workflow run. A failure is reported by an exception, never as a status that could be mistaken for "nothing to do". */
public enum ApprovalStatus {
    /** Approval is explicitly disabled for the entity; nothing else was called. */
    SKIPPED,
    /** Approval is enabled; later phases (rules, hierarchy, approval) have not run yet. */
    ELIGIBLE,
    IN_PROGRESS,
    APPROVED,
    NO_APPLICABLE_RULE,
    PARTIALLY_APPROVED,
    CONFIGURATION_ERROR,
    FAILED
}
