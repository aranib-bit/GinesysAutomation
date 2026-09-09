package com.gsl.reports;

import java.util.ArrayList;
import java.util.List;

/**
 * Thread-local scratch space for one step's synthesized summary fragments — plan §Point 9
 * ("every report section should end with a synthesized, business-readable summary of what
 * happened, not just a flat list of individual action lines").
 *
 * <p>Call {@link #record(String)} (or {@link #record(String, boolean)} when the fragment
 * represents a pass/fail-style check) as many times as needed from anywhere inside a
 * {@link StepExecutor#step(String, StepExecutor.StepAction)} action's own business logic — each
 * fragment can be a plain one-line sentence or a small pre-built HTML snippet (e.g. a labeled
 * table). {@code step(...)} itself drains everything recorded via {@link #flush()} right before
 * the step's node closes on the SUCCESS path, and renders one consolidated "Summary" box as the
 * last thing in that section — the "TL;DR" a reader sees right before the green "Step passed"
 * marker, instead of having to piece the outcome together from the individual lines above it.
 *
 * <p>Mirrors {@link ActionTrailBuffer}/{@link StepDurationBuffer}'s shape (small thread-local
 * accumulators keyed to the current step) but is deliberately PER-STEP, not cumulative across the
 * whole test: {@link #flush()} always clears, so a step that records nothing leaves nothing behind
 * for the next one, and a step that never gets its fragments flushed (the FAILURE path — see
 * {@code StepExecutor}, which flushes-and-discards there rather than rendering a possibly
 * misleading "all passed"-style summary on a step that didn't actually finish) doesn't leak stale
 * fragments into whatever step runs next on this thread.
 */
public final class StepSummaryBuffer {

    /** One recorded fragment plus whether it renders with a tick (&#10003;) or a cross (&#10007;)
     *  bullet — mirrors the same tick/cross convention {@code FinVchMainBaseValidator}'s
     *  "Level 1" checklist box already uses. Ignored for a fragment that is already its own HTML
     *  block (e.g. a table) — see {@code StepExecutor#renderStepSummary}. */
    public record Fragment(String text, boolean ok) {}

    private static final ThreadLocal<List<Fragment>> FRAGMENTS = ThreadLocal.withInitial(ArrayList::new);

    private StepSummaryBuffer() {}

    /** Appends one summary fragment for the CURRENTLY EXECUTING step, rendered with a tick
     *  bullet. No-op for null/blank. */
    public static void record(String fragment) {
        record(fragment, true);
    }

    /** Same as {@link #record(String)}, but lets the caller mark the fragment as a negative/
     *  blocked outcome — rendered with a cross bullet instead of a tick — for a check that
     *  genuinely didn't go the expected way but isn't itself what fails the step (e.g. Delete's
     *  "Unpost if Posted" being blocked by BRS/closed-period, which the step still handles). */
    public static void record(String fragment, boolean ok) {
        if (fragment == null || fragment.isBlank()) return;
        FRAGMENTS.get().add(new Fragment(fragment, ok));
    }

    /** Drains and clears every fragment recorded since the last flush. Always clears, even when
     *  the caller discards the result (the failure path) — so nothing leaks into the next step. */
    public static List<Fragment> flush() {
        List<Fragment> current = FRAGMENTS.get();
        List<Fragment> copy = List.copyOf(current);
        current.clear();
        return copy;
    }
}
