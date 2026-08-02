package com.gsl.reports;

import com.gsl.reports.contract.DomainReporter;
import com.gsl.reports.contract.TestResultsAccumulator;
import com.gsl.reports.contract.TestResultsRenderer;
import com.gsl.testdata.dto.MergedFinanceApVoucherData;
import com.gsl.testdata.dto.MergedFinanceArVoucherData;

import java.util.Arrays;

/**
 * Finance domain plug-in for the universal reporting registry.
 *
 * <p>Registered once in {@link com.gsl.listeners.TestListener#onStart(org.testng.ISuite)}.
 * Handles both AP and AR Voucher tests; the Finance-specific DTO imports live here,
 * keeping {@link com.gsl.listeners.TestListener} domain-agnostic.
 *
 * <p>Adding a new Finance test type (e.g. Journal Voucher) extends this class or
 * registers a sibling {@link DomainReporter} — zero changes to TestListener.
 */
public final class FinanceDomainReporter implements DomainReporter {

    private static final TestResultsRenderer RENDERER = ActualResultsLogger.renderer();

    // ── DomainReporter contract ───────────────────────────────────────────────

    @Override
    public boolean supports(Object[] params) {
        return Arrays.stream(params).anyMatch(
                p -> p instanceof MergedFinanceApVoucherData
                  || p instanceof MergedFinanceArVoucherData);
    }

    @Override
    public Object extractTestData(Object[] params) {
        return Arrays.stream(params)
                .filter(p -> p instanceof MergedFinanceApVoucherData
                          || p instanceof MergedFinanceArVoucherData)
                .findFirst().orElse(null);
    }

    @Override
    public String buildSentence(Object testData) {
        if (testData instanceof MergedFinanceArVoucherData)
            return ArVoucherDescriptionBuilder.build((MergedFinanceArVoucherData) testData);
        if (testData instanceof MergedFinanceApVoucherData)
            return ApVoucherDescriptionBuilder.build((MergedFinanceApVoucherData) testData);
        return null;
    }

    /**
     * Returns the test-case name when it carries meaningful content beyond the
     * bare method name (e.g. "Bank GL — Partial Adjustment — NONE").
     * Returns {@code null} for the common-case method-name-default values so
     * TestListener falls back to the sentence or method description.
     */
    @Override
    public String buildRichName(Object testData) {
        if (testData instanceof MergedFinanceArVoucherData) {
            MergedFinanceArVoucherData ar = (MergedFinanceArVoucherData) testData;
            if (ar.getMasterTestData() == null) return null;
            String tcName = ar.getMasterTestData().getTestcasename();
            if (tcName != null && !tcName.isBlank()
                    && !tcName.equalsIgnoreCase("createArVoucher"))
                return tcName;
        } else if (testData instanceof MergedFinanceApVoucherData) {
            MergedFinanceApVoucherData ap = (MergedFinanceApVoucherData) testData;
            if (ap.getMasterTestData() == null) return null;
            String tcName = ap.getMasterTestData().getTestcasename();
            if (tcName != null && !tcName.isBlank()
                    && !tcName.equalsIgnoreCase("createApVoucherAdvancePayment")
                    && !tcName.equalsIgnoreCase("createApVoucherDocumentPayment"))
                return tcName;
        }
        return null;
    }

    @Override
    public TestResultsAccumulator getAccumulator() {
        return ActualResultsHolder.get();
    }

    @Override
    public void onTestSuccess(TestResultsAccumulator accumulator) {
        try {
            RENDERER.flush(accumulator);
        } finally {
            accumulator.resetAccumulator();
        }
    }

    @Override
    public void onTestFailure(TestResultsAccumulator accumulator) {
        try {
            RENDERER.flush(accumulator);
        } finally {
            accumulator.resetAccumulator();
        }
    }
}
