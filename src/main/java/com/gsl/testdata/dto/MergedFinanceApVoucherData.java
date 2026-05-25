package com.gsl.testdata.dto;

import com.gsl.testdata.Finance.Operations.Vouchers.APVoucher.*;
import com.gsl.testdata.InvocationData;
import com.gsl.testdata.MasterTestData;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
@AllArgsConstructor
public class MergedFinanceApVoucherData implements InvocationData {

    private MasterTestData masterTestData;

    private DocumentInformation documentInformation;

    private List<SubLedgerInformation> subLedgerInformationList;

    private List<SelectDocument> selectDocumentList;

    private List<ReferenceSitewiseFIFOsetoff> referenceSitewiseFIFOList;

    private List<PaymentInformation> paymentInformationList;

    private List<TdsAndNetPayable> tdsAndNetPayableList;

    // Derived flags
    private boolean onAccount;
    private boolean tdsProvided;
    private boolean paymentProvided;
    private boolean needsIgnoreProceed;

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
