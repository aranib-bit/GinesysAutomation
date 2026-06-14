package com.gsl.testdata.dto;

import com.gsl.flow.Finance.Operations.Common.PostSaveDialogHandler;
import com.gsl.testdata.Finance.Operations.Vouchers.ARVoucher.*;
import com.gsl.testdata.InvocationData;
import com.gsl.testdata.MasterTestData;
import com.gsl.utils.dataprovider.fixture.finance.ArVoucherFixtureOptions;
import com.gsl.utils.dataprovider.fixture.finance.ArVoucherScenarioType;
import com.gsl.utils.dataprovider.fixture.finance.NegativeValidation;
import com.gsl.utils.dataprovider.fixture.finance.SelectDocumentVariant;
import com.gsl.validation.db.finance.VoucherDbContextBuilder;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Data
@Builder
@AllArgsConstructor
public class MergedFinanceArVoucherData
        implements InvocationData,
                   PostSaveDialogHandler.PostSaveSpec,
                   VoucherDbContextBuilder.InvoiceScoped {

    private MasterTestData masterTestData;

    private DocumentInformation documentInformation;

    private List<SubLedgerInformation> subLedgerInformationList;

    private List<SelectDocument> selectDocumentList;

    private List<ReferenceSitewiseFIFOsetoff> referenceSitewiseFIFOList;

    private List<PaymentInformation> paymentInformationList;

    // Derived flags
    private boolean paymentProvided;
    private boolean needsPaymentInfoWarning;
    private boolean tdsProvided;

    /** Grid Amount Cr vs total adjustment entry amount relationship.
     *  DB mode: set from SelectDocumentVariant.gridRelation.
     *  Excel mode: set from DocumentInformation gridRelation column (blank = EQUAL).
     *  Defaults to EQUAL so existing tests without this field are unaffected.
     */
    @Builder.Default
    private SelectDocumentVariant.GridRelation gridRelation = SelectDocumentVariant.GridRelation.EQUAL;

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

    /**
     * P3: Structured log lines from the DB data-gathering phase (populated by
     * {@code DbArVoucherDataSource.loadFreshForScenario()}). Empty for Excel-mode tests.
     * Rendered by {@code DbGatheringExtentLogger} in the report before Test Data tables.
     */
    @Builder.Default
    private List<String> dbGatheringLog = new ArrayList<>();

    /**
     * Non-null for negative-scenario variants (T-03); {@code null} for positive scenarios.
     * Carries the expected UI validation error message. Mirrors
     * {@link com.gsl.testdata.dto.MergedFinanceApVoucherData#getExpectedValidation()}.
     * Used by {@code ArVoucherPredicateFactory.NEGATIVE_FORM_VALIDATION_BEHAVIOR}.
     */
    private NegativeValidation expectedValidation;

    @Override
    public int getInvocationIndex() {
        return invocationIndex;
    }

    @Override
    public int getTotalInvocations() {
        return totalInvocations;
    }

    /**
     * Scoped invoice numbers for the finpost pre/post snapshot (Save sub-steps 5a/5e).
     * Returns the SelectDocument doc-numbers, or an empty list when none are selected.
     */
    @Override
    public List<String> resolveInvoiceNos() {
        return selectDocumentList != null
                ? selectDocumentList.stream()
                        .map(SelectDocument::getDocumentNumber)
                        .collect(Collectors.toList())
                : Collections.emptyList();
    }
}