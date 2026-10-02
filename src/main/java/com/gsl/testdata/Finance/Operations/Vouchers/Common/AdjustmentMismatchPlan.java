package com.gsl.testdata.Finance.Operations.Vouchers.Common;

import com.gsl.enums.AdjustmentMismatchAction;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Ordered script for the amount-mismatch Confirmation raised by Adjustment Entry's OK button.
 * Each {@link Step} answers one appearance of the popup; the OK click that follows the last step
 * must not raise it again unless that last step was {@link AdjustmentMismatchAction#UPDATE_PAYMENT_AMOUNT}
 * (which ends the flow). An empty plan asserts the popup does NOT appear.
 *
 * <p>Examples: {@link #none()}; {@link #updatePayment()}; {@link #adjustMore(AdjustmentSpec...)};
 * {@link #adjustMoreThenUpdate(AdjustmentSpec)} (add one more document, still short, then accept).
 */
public final class AdjustmentMismatchPlan {

    public static final class Step {
        private final AdjustmentMismatchAction action;
        private final AdjustmentSpec extra;

        private Step(AdjustmentMismatchAction action, AdjustmentSpec extra) {
            this.action = action;
            this.extra = extra;
        }

        public AdjustmentMismatchAction getAction() { return action; }
        /** Non-null only for {@code ADJUST_MORE}. */
        public AdjustmentSpec getExtra() { return extra; }
    }

    private static final AdjustmentMismatchPlan NONE = new AdjustmentMismatchPlan(Collections.emptyList());

    private final List<Step> steps;

    private AdjustmentMismatchPlan(List<Step> steps) {
        this.steps = Collections.unmodifiableList(steps);
    }

    public List<Step> getSteps() { return steps; }

    public boolean isExpectingPopup() { return !steps.isEmpty(); }

    public AdjustmentMismatchAction firstAction() {
        return steps.isEmpty() ? AdjustmentMismatchAction.NONE : steps.get(0).action;
    }

    public AdjustmentMismatchAction lastAction() {
        return steps.isEmpty() ? AdjustmentMismatchAction.NONE : steps.get(steps.size() - 1).action;
    }

    public static AdjustmentMismatchPlan none() { return NONE; }

    public static AdjustmentMismatchPlan updatePayment() {
        return new AdjustmentMismatchPlan(List.of(new Step(AdjustmentMismatchAction.UPDATE_PAYMENT_AMOUNT, null)));
    }

    /** One "I will adjust more" answer per supplied extra adjustment, in order; the OK after the
     *  last one must succeed without another popup (adjusted total now equals payment). */
    public static AdjustmentMismatchPlan adjustMore(AdjustmentSpec... extras) {
        List<Step> s = new ArrayList<>();
        for (AdjustmentSpec e : extras) s.add(new Step(AdjustmentMismatchAction.ADJUST_MORE, e));
        return new AdjustmentMismatchPlan(s);
    }

    public static AdjustmentMismatchPlan adjustMoreThenUpdate(AdjustmentSpec extra) {
        List<Step> s = new ArrayList<>();
        s.add(new Step(AdjustmentMismatchAction.ADJUST_MORE, extra));
        s.add(new Step(AdjustmentMismatchAction.UPDATE_PAYMENT_AMOUNT, null));
        return new AdjustmentMismatchPlan(s);
    }
}
