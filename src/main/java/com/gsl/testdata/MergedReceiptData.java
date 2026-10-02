package com.gsl.testdata;

import com.gsl.testdata.Procurement.ReceiptHeader;
import com.gsl.testdata.Procurement.ScanItemWithDetails;

import java.util.List;

/**
 * One merged, invocation-ready data-provider row for a Procurement receipt test — pairs the
 * generic {@link MasterTestData} (test-case metadata) with the receipt-specific
 * {@link ReceiptHeader} and its scanned {@link ScanItemWithDetails} lines, plus this row's
 * position within the current test method's total invocations (see {@link InvocationData}).
 *
 * <p>This is a legacy/parallel counterpart to {@code com.gsl.testdata.dto.MergedReceiptData}:
 * this class (package {@code com.gsl.testdata}) is consumed by
 * {@code com.tests.Procurement.Operations.ReceiptsDataTest} and {@code com.tests.DataSupplierTest},
 * while the {@code dto} package's sibling is consumed by
 * {@code MergedReceiptDataSupplier}/{@code MergedReceiptTest}/{@code Receipts} — both are live,
 * they are not interchangeable, and neither supersedes the other.
 */
public class MergedReceiptData implements InvocationData{
    private MasterTestData masterTestData;
    private ReceiptHeader receiptHeader;
    private List<ScanItemWithDetails> scanItems;

    // New fields required for InvocationData
    private int invocationIndex;
    private int totalInvocations;

    /** Assembles one invocation row from its test-case metadata, receipt header, and scanned item lines. */
    public MergedReceiptData (MasterTestData masterTestData, ReceiptHeader receiptHeader, List<ScanItemWithDetails> scanItems,int invocationIndex,
                             int totalInvocations) {
        this.masterTestData = masterTestData;
        this.receiptHeader = receiptHeader;
        this.scanItems = scanItems;

        this.invocationIndex = invocationIndex;
        this.totalInvocations = totalInvocations;
    }

    public MasterTestData getMasterTestData() {
        return masterTestData;
    }

    public ReceiptHeader getReceiptHeader() {
        return receiptHeader;
    }

    public List<ScanItemWithDetails> getScanItems() {
        return scanItems;
    }

    @Override
    public int getInvocationIndex() {
        return invocationIndex;
    }

    @Override
    public int getTotalInvocations() {
        return totalInvocations;
    }
}
