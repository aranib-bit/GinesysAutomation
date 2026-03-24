package com.gsl.testdata.dto;

import com.gsl.testdata.InvocationData;
import com.gsl.testdata.MasterTestData;
import com.gsl.testdata.Procurement.ReceiptHeader;
import com.gsl.testdata.dto.ItemBlock;
import lombok.Builder;
import lombok.Data;

import java.util.List;

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