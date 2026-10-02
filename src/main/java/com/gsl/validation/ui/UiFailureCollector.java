package com.gsl.validation.ui;

import java.util.ArrayList;
import java.util.List;

/**
 * Per-test holder of UI-contract FAILs that were rendered into the report but deliberately NOT
 * thrown yet ({@link UiValidationReporter#renderDeferred}). A UI defect — even a cosmetic one —
 * must never stop the rest of the flow (Save, DB validation, snapshot validation) from running, so
 * the failures wait here until the flow's final assertion point
 * ({@code VoucherSaveFlowSteps#finalizeAsserts} for Create, the end of the test for Edit), where
 * they are reported together with any DB / snapshot failure in one combined error.
 *
 * <p>Thread-local, one test at a time per thread; {@link #clear()} is called at test start.
 */
public final class UiFailureCollector {

    private UiFailureCollector() {}

    private static final ThreadLocal<List<UiValidationFailure>> FAILS = ThreadLocal.withInitial(ArrayList::new);

    public static void addAll(List<UiValidationFailure> failures) {
        failures.stream().filter(UiValidationFailure::isFail).forEach(FAILS.get()::add);
    }

    /** A copy of the pending failures, in the order they were recorded. */
    public static List<UiValidationFailure> pending() {
        return new ArrayList<>(FAILS.get());
    }

    public static boolean hasPending() {
        return !FAILS.get().isEmpty();
    }

    public static void clear() {
        FAILS.remove();
    }

    /** For a flow that failed with {@code cause} while UI failures were pending: returns a combined
     *  error naming both (and clears the pending list); returns {@code cause} unchanged when nothing
     *  is pending and it already is an unchecked throwable. */
    public static RuntimeException combineWithPending(Throwable cause) {
        if (!hasPending()) {
            if (cause instanceof RuntimeException re) return re;
            if (cause instanceof Error err) throw err;
            return new RuntimeException(cause);
        }
        List<UiValidationFailure> pending = pending();
        clear();
        // Errors cannot be returned as RuntimeException, so the combined AssertionError is thrown directly.
        com.gsl.validation.CombinedValidationError combined = new com.gsl.validation.CombinedValidationError(List.of(
                com.gsl.validation.CombinedValidationError.uiArea(pending),
                com.gsl.validation.CombinedValidationError.areaFrom(
                        com.gsl.validation.CombinedValidationError.Kind.OTHER, "Test flow", cause)));
        combined.initCause(cause);
        throw combined;
    }

    /** Throws one error listing the pending UI failures (and clears them); no-op when none. Use at a
     *  flow's end when there is no DB/snapshot area to combine with. */
    public static void assertNoPending() {
        if (!hasPending()) return;
        List<UiValidationFailure> pending = pending();
        clear();
        throw new com.gsl.validation.CombinedValidationError(List.of(
                com.gsl.validation.CombinedValidationError.uiArea(pending)));
    }
}
