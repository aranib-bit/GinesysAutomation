package com.gsl.approval.reporting;

import java.util.function.Supplier;

/**
 * Where the approval workflow reports its steps. The workflow reports through this, so it runs the same in a unit test (no report) and in a
 * real run (an "Approval Workflow" node with one child step per stage). Never given a token, password or header.
 */
public interface ApprovalReporter {

    /** Runs {@code action} as one named step; a failure marks the step failed and propagates. */
    void step(String name, Runnable action);

    /** Runs {@code action} as one named step and returns its value. */
    <T> T call(String name, Supplier<T> action);

    /** A plain information line inside the current step. */
    void info(String message);

    /** Does not report anything: the steps simply run. */
    ApprovalReporter NONE = new ApprovalReporter() {
        @Override
        public void step(String name, Runnable action) {
            action.run();
        }

        @Override
        public <T> T call(String name, Supplier<T> action) {
            return action.get();
        }

        @Override
        public void info(String message) {
            // nothing to report to
        }
    };
}
