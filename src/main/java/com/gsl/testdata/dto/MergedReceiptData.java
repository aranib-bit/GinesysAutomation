package com.gsl.testdata.dto;

import com.gsl.testdata.InvocationData;
import com.gsl.testdata.MasterTestData;
import com.gsl.testdata.Procurement.ReceiptHeader;
import com.gsl.testdata.dto.ItemBlock;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Merged test data for one Procurement Receipt creation invocation, produced by {@code
 * MergedReceiptDataSupplier} (not to be confused with the older, unrelated {@code
 * com.gsl.testdata.MergedReceiptData}). Joins the shared {@code MasterTestData} row for the
 * invoking test method with its mandatory {@link ReceiptHeader} and its {@link #itemBlocks} — one
 * {@link ItemBlock} per item/set line on the receipt, each carrying whichever selection-detail
 * rows apply to it (see {@link ItemBlock}'s Javadoc).
 *
 * <p>{@code flatMap=true} on the supplier's data method — a {@code TestCaseId} whose {@code
 * MasterTestData.getInvocationcount()} is greater than 1 is expanded into that many separate
 * invocations ({@link #invocationIndex}/{@link #totalInvocations}), each otherwise carrying
 * identical joined data.
 */
@Data
@Builder
public class MergedReceiptData implements InvocationData {

    private String testCaseId;
    private MasterTestData masterTestData;
    private ReceiptHeader receiptHeader;

    private List<ItemBlock> itemBlocks;

    // Invocation metadata
    private int invocationIndex;
    private int totalInvocations;

    @Override
    public int getInvocationIndex() {
        return invocationIndex;
    }

    @Override
    public int getTotalInvocations() {
        return totalInvocations;
    }
}