package com.gsl.testdata.dto;

import com.gsl.testdata.Finance.Operations.Vouchers.APVoucher.*;
import com.gsl.testdata.InvocationData;
import com.gsl.testdata.MasterTestData;
import com.gsl.utils.dataprovider.fixture.finance.ApVoucherExecutionGroup;
import com.gsl.utils.dataprovider.fixture.finance.ApVoucherFixtureOptions;
import com.gsl.utils.dataprovider.fixture.finance.ApVoucherScenarioType;
import com.gsl.utils.dataprovider.fixture.finance.ApVoucherTag;
import com.gsl.utils.dataprovider.fixture.finance.NegativeValidation;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

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
    private boolean needsPaymentInfoWarning;

    // ─── DB mode fields ───────────────────────────────────────────────────────

    /**
     * DB mode scenario type. {@code null} in Excel mode.
     * Set by {@link ApVoucherScenarioType#loadData(com.gsl.utils.dataprovider.fixture.LiveDbRefs)}
     * so the test method can detect a placeholder vs. fully-loaded data.
     */
    private ApVoucherScenarioType scenarioType;

    /**
     * DB query parameters carried from placeholder generation (DataSupplier time)
     * through to lazy execution-time loading ({@code DbApVoucherDataSource.loadFreshForScenario}).
     * {@code null} in Excel mode.
     */
    private ApVoucherFixtureOptions fixtureOptions;

    /**
     * Structured log of DB data-gathering steps, rendered by
     * {@code DbGatheringExtentLogger} in the Extent Report before test steps begin.
     */
    @Builder.Default
    private List<String> dbGatheringLog = new ArrayList<>();

    /** TDS Amount per sub-ledger lineNo, captured at runtime from the TDS popup. Keyed by lineNo. */
    @Builder.Default
    private Map<String, BigDecimal> capturedTdsAmounts = new HashMap<>();

    /** Net Payable per sub-ledger lineNo, captured at runtime from the TDS popup. Keyed by lineNo. */
    @Builder.Default
    private Map<String, BigDecimal> capturedNetPayables = new HashMap<>();

    /**
     * Non-null for negative-scenario variants; {@code null} for positive scenarios.
     * Carries the expected UI validation error message.
     * Used by {@link com.gsl.utils.dataprovider.fixture.finance.ApVoucherPredicateFactory#NEGATIVE_FORM_VALIDATION_BEHAVIOR}.
     */
    private NegativeValidation expectedValidation;

    /**
     * Tag set assigned at placeholder-build time by {@code DbApVoucherDataSource.tagsFor()}.
     * Used by the {@code apv.tag} filter to include/exclude this placeholder.
     * Empty set = no filtering applied (all tags match).
     */
    @Builder.Default
    private Set<ApVoucherTag> tags = Collections.emptySet();

    // ─── Invocation metadata ──────────────────────────────────────────────────

    private int invocationIndex;
    private int totalInvocations;

    public Optional<BigDecimal> getTdsAmountForLine(String lineNo) {
        if (lineNo == null || capturedTdsAmounts == null) return Optional.empty();
        return Optional.ofNullable(capturedTdsAmounts.get(lineNo));
    }

    public Optional<BigDecimal> getNetPayableForLine(String lineNo) {
        if (lineNo == null || capturedNetPayables == null) return Optional.empty();
        return Optional.ofNullable(capturedNetPayables.get(lineNo));
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
