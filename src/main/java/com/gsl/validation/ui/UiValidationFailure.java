package com.gsl.validation.ui;

import java.util.List;

/**
 * One checked UI-contract property, e.g. "GV_OWNER_SITE_LABEL — font-size: expected
 * 'PLACEHOLDER_FONT_SIZE' but got '13px'" — despite the class name (kept to avoid a mass rename
 * across every existing caller), an instance can represent a PASS, a FAIL or a SKIP: every
 * {@code UiComponentValidator} check records one entry per property checked, not just
 * mismatches, so a clean run proves the checks actually ran instead of only proving nothing broke.
 * SKIP means "not applicable / not displayed" (e.g. a USER-optional grid column that is hidden,
 * Allocate Sites on a row with no Site) — never soft-asserted, counted separately.
 * Collected into a list per component/page; {@link UiValidationReporter#renderAndAssert} renders
 * the full list and soft-asserts only the {@link #isFail()} ones, same collect-all-then-assert
 * convention as this codebase's DB/snapshot validators.
 *
 * <p>{@link #path()} is the {@link UiValidationScope} node stack active when the instance was
 * created (empty for results created outside any node) — the reporter's tree is built from it.
 */
public final class UiValidationFailure {

    /** {@code KNOWN_ISSUE}: a mismatch that matches a registered {@link UiKnownIssue} — shown amber
     *  with its note, never counted as a failure and never soft-asserted. */
    public enum Status { PASS, FAIL, SKIP, KNOWN_ISSUE }

    private final String component;
    private final String property;
    private final String expected;
    private final String actual;
    private final Status status;
    private final String note;
    private final List<String> path;

    /** Every existing call site uses this constructor, which always means FAIL — predates the
     *  PASS/FAIL distinction, when every recorded entry WAS a failure by construction. */
    public UiValidationFailure(String component, String property, String expected, String actual) {
        this(component, property, expected, actual, Status.FAIL);
    }

    public UiValidationFailure(String component, String property, String expected, String actual, Status status) {
        this.component = component;
        this.property = property;
        this.expected = expected;
        this.actual = actual;
        // A would-be FAIL that exactly matches a registered known issue is recorded as KNOWN_ISSUE
        // (keeping the real expected/actual) — one central place, so every validator gets it.
        java.util.Optional<String> known = status == Status.FAIL
                ? UiKnownIssues.match(component, property, actual) : java.util.Optional.empty();
        this.status = known.isPresent() ? Status.KNOWN_ISSUE : status;
        this.note = known.orElse(null);
        this.path = UiValidationScope.currentPath();
    }

    public String component() { return component; }
    public String property()  { return property; }
    public String expected()  { return expected; }
    public String actual()    { return actual; }
    public Status status()    { return status; }
    public List<String> path() { return path; }

    public boolean isFail() { return status == Status.FAIL; }
    public boolean isPass() { return status == Status.PASS; }
    public boolean isSkip() { return status == Status.SKIP; }
    public boolean isKnownIssue() { return status == Status.KNOWN_ISSUE; }
    /** The registered known-issue note, or {@code null} when this is not a known issue. */
    public String note() { return note; }

    /** Compact one-line label for failure summaries: {@code Section > Row > Component: property
     *  expected 'x', actual 'y'}. Row-title chips (" · Site: yes · …") are dropped from the path. */
    public String shortLabel() {
        List<String> clean = new java.util.ArrayList<>();
        for (String seg : path) {
            String s = seg.replaceAll(" · (Sub Ledger|Site|Payment): (yes|no)", "");
            clean.add(s);
        }
        return (clean.isEmpty() ? "" : String.join(" > ", clean) + " > ")
                + component + ": " + property + " expected '" + expected + "', actual '" + actual + "'";
    }

    @Override
    public String toString() {
        return (path.isEmpty() ? "" : String.join(" > ", path) + " > ")
                + component + " — " + property + ": expected '" + expected + "' but got '" + actual + "'";
    }
}
