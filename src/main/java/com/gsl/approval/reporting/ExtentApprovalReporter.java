package com.gsl.approval.reporting;

import com.aventstack.extentreports.ExtentTest;
import com.gsl.reports.ReportLogger;
import com.gsl.reports.StepExecutor;

import java.util.function.Supplier;

/** {@link ApprovalReporter} on the framework's Extent report: one "Approval Workflow" group under the current test, a child step per stage. */
public class ExtentApprovalReporter implements ApprovalReporter {

    private final String title;
    private ExtentTest group;

    public ExtentApprovalReporter(String title) {
        this.title = title;
    }

    private synchronized ExtentTest group() {
        if (group == null) {
            group = StepExecutor.group(title);
        }
        return group;
    }

    @Override
    public void step(String name, Runnable action) {
        StepExecutor.stepUnder(group(), name, action::run);
    }

    @Override
    public <T> T call(String name, Supplier<T> action) {
        Object[] holder = new Object[1];
        StepExecutor.stepUnder(group(), name, () -> holder[0] = action.get());
        @SuppressWarnings("unchecked")
        T value = (T) holder[0];
        return value;
    }

    @Override
    public void info(String message) {
        ReportLogger.business("[Approval] {}", message);
    }
}
