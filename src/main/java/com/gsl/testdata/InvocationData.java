package com.gsl.testdata;

/**
 * Marks a merged test-data DTO as one row of a multi-invocation data-provider run, so
 * {@code TestListener} can identify and label each invocation without knowing the concrete DTO
 * type. Implementations are scanned for structurally by {@code TestListener} (it filters a test
 * method's parameters for {@code instanceof InvocationData}), so any data-provider whose rows
 * represent "row N of M" for a single test method should implement this — see
 * {@link MergedReceiptData}, {@code com.gsl.testdata.dto.MergedReceiptData},
 * {@code com.gsl.testdata.dto.MergedFinanceGeneralVoucherData}, and
 * {@code com.gsl.testdata.Inventory.MergedInventoryItemData} for existing implementers.
 */
public interface InvocationData {
    /** 1-based (or 0-based, per implementer) index of this row within the current test's total invocations. */
    int getInvocationIndex();
    /** Total number of invocations (data rows) the current test method is running. */
    int getTotalInvocations();
}
