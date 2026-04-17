package com.gsl.testdata.dto;

import com.gsl.testdata.Finance.Operations.Vouchers.ARVoucher.*;
import com.gsl.testdata.InvocationData;
import com.gsl.testdata.MasterTestData;
import com.gsl.utils.dataprovider.fixture.finance.ArVoucherFixtureOptions;
import com.gsl.utils.dataprovider.fixture.finance.ArVoucherScenarioType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
@AllArgsConstructor
public class MergedFinanceArVoucherData implements InvocationData {

    private MasterTestData masterTestData;

    private DocumentInformation documentInformation;

    private List<SubLedgerInformation> subLedgerInformationList;

    private List<SelectDocument> selectDocumentList;

    private List<ReferenceSitewiseFIFOsetoff> referenceSitewiseFIFOList;

    private List<PaymentInformation> paymentInformationList;

    // Derived flags
    private boolean paymentProvided;
    private boolean needsIgnoreProceed;

    // Invocation metadata
    private int invocationIndex;
    private int totalInvocations;

    /**
     * Non-null for DB-mode test data; null for Excel-mode (legacy path).
     * The test method delegates UI interaction + assertion to
     * {@link ArVoucherScenarioType#execute(MergedFinanceArVoucherData,
     * com.gsl.pages.Finance.Operations.ArVoucherAdd)} when this is set.
     */
    private ArVoucherScenarioType scenarioType;

    /**
     * DB query limits carried from placeholder to Phase 2 lazy loading.
     * Non-null in DB mode; null in Excel mode (not used by Excel path).
     */
    private ArVoucherFixtureOptions fixtureOptions;

    @Override
    public int getInvocationIndex() {
        return invocationIndex;
    }

    @Override
    public int getTotalInvocations() {
        return totalInvocations;
    }
}