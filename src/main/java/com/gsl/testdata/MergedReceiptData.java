package com.gsl.testdata;

import com.gsl.testdata.Procurement.ReceiptHeader;
import com.gsl.testdata.Procurement.ScanItemWithDetails;

import java.util.List;

public class MergedReceiptData implements InvocationData{
    private MasterTestData masterTestData;
    private ReceiptHeader receiptHeader;
    private List<ScanItemWithDetails> scanItems;

    // New fields required for InvocationData
    private int invocationIndex;
    private int totalInvocations;

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
