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
     * The {@code scheme_docno} of an existing AR Voucher to operate on.
     * Populated by Edit and Delete DataSources; null in Create mode.
     * Used by test classes to locate the voucher in the list page.
     */
    private String existingVoucherNo;

    /**
     * {@code release_status} of the voucher as read from {@code finvchmain}.
     * <ul>
     *   <li>{@code 'N'} — never posted → directly editable/deletable</li>
     *   <li>{@code 'U'} — unposted     → directly editable/deletable</li>
     *   <li>{@code 'P'} — posted       → test class must unpost, then check
     *       {@code fintag}; if tagged, remove via Document Adjustment first</li>
     * </ul>
     * Null in Create mode (not relevant).
     */
    private String releaseStatus;

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