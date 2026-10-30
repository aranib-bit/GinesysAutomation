package com.gsl.utils.dataprovider.source.finance;

import com.gsl.database.DataAvailabilityGuard;
import com.gsl.database.DatabaseManager;
import com.gsl.database.DbQueryBuffer;
import com.gsl.database.dao.finance.FinVchDetDao;
import com.gsl.enums.PaymentSelectionType;
import com.gsl.model.finance.FinVchDet;
import com.gsl.model.finance.FinVchMain;
import com.gsl.testdata.Finance.Operations.Vouchers.APVoucher.ApVoucherDetailLineEditTarget;
import com.gsl.testdata.Finance.Operations.Vouchers.APVoucher.ApVoucherPaymentInfoEditTarget;
import com.gsl.testdata.Finance.Operations.Vouchers.APVoucher.DocumentInformation;
import com.gsl.testdata.Finance.Operations.Vouchers.APVoucher.PaymentInformation;
import com.gsl.testdata.dto.MergedFinanceApVoucherData;
import com.gsl.utils.Faker.FakerUtils;
import com.gsl.utils.Log;
import com.gsl.utils.dataprovider.fixture.finance.ApVoucherEditScenarioType;
import com.gsl.utils.dataprovider.fixture.finance.ApVoucherExecutionGroup;
import com.gsl.utils.dataprovider.fixture.finance.ApVoucherTagState;
import com.gsl.utils.dataprovider.source.TestDataSource;
import org.jdbi.v3.core.Jdbi;
import org.testng.SkipException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * DB-driven test-data source for {@code editApVoucherHeader}/{@code editApVoucherDateFieldDisabled}
 * — mirrors {@link DbApVoucherRevertDataSource}'s exact claim/backfill shape (via
 * {@link ApVoucherTagStateClaimSupport}), additionally building a new {@link DocumentInformation}
 * carrying a fresh reference/remarks (same {@code FakerUtils.randomReference()}/
 * {@code randomRemarks()} idiom {@code DbArVoucherEditDataSource} already uses for AR's own Edit
 * source). Branches on {@code methodName} (passed by {@code MergedFinApVoucherDataSupplier}) so
 * the negative {@code editApVoucherDateFieldDisabled} case can draw its own row, gated under its
 * own execution group, without disturbing {@code editApVoucherHeader}'s existing MANDATORY rows.
 *
 * <p>{@code voucherno}/{@code cashbank}/{@code ownersite}/{@code onAccount} are all left
 * {@code null} on the built {@link DocumentInformation} — same reasoning
 * {@code ExcelApVoucherEditDataSource}'s own Javadoc documents: {@code EditApVoucher} itself
 * resolves the live On Account state right before filling the form, and
 * {@code voucherno}/{@code cashbank} are unchanged-on-Edit fields. {@code date} is likewise left
 * as the voucher's own existing value (not freshly resolved) — AP Voucher's Date field is
 * genuinely disabled on Edit by business rule (confirmed live DOM, run 20260930_012130:
 * {@code disabled="" aria-disabled="true"}), so no positive scenario should ever attempt to change
 * it; {@code editApVoucherDateFieldDisabled} is where that business rule itself gets asserted.
 */
public class DbApVoucherEditDataSource implements TestDataSource<MergedFinanceApVoucherData> {

    private final String xmlGroupParam;

    public DbApVoucherEditDataSource() {
        this(null);
    }

    /** @param xmlGroupParam fallback {@code apv.group} value from TestNG {@code <parameter>} */
    public DbApVoucherEditDataSource(String xmlGroupParam) {
        this.xmlGroupParam = xmlGroupParam;
    }

    @Override
    public List<MergedFinanceApVoucherData> load(String methodName) {
        ApVoucherExecutionGroup activeGroup = ApVoucherTagStateClaimSupport.resolveActiveGroup(xmlGroupParam);
        Jdbi jdbi = DatabaseManager.getJdbi();

        List<MergedFinanceApVoucherData> results = new ArrayList<>();
        // editApVoucherDateFieldDisabled — the negative case asserting AP Voucher's Date field is
        // genuinely disabled on Edit (business rule; see resolveOneCandidate's own comment) — gated
        // under REGRESSION_PHASE1, not MANDATORY, per the user's explicit direction. Reuses the
        // same claim/candidate shape as the positive scenario below (tag state is irrelevant to a
        // pure UI-state assertion — one UNTAGGED candidate is enough, no need to also spend a
        // scarce TAGGED one here).
        if ("editApVoucherDateFieldDisabled".equals(methodName)) {
            if (ApVoucherTagStateClaimSupport.matchesGroup(activeGroup, ApVoucherExecutionGroup.REGRESSION_PHASE1)) {
                results.add(resolveOneCandidate(jdbi, ApVoucherTagState.UNTAGGED));
            }
        } else if ("editApVoucherDetailLine".equals(methodName)) {
            // MANDATORY only, per Round 12's own staged rollout — combinations #1 (Document, TAGGED,
            // amount change via re-adjustment) and #2 (Advance, UNTAGGED, plain field edit). Both
            // rows drive the SAME test method (editApVoucherDetailLine); which mechanism applies is
            // resolved per-row here, not fixed by the method — see MergedFinanceApVoucherData
            // #getEditScenarioType().
            // Document mode now runs through the multi-document adjustment scenarios (capture ALL tags,
            // answer the amount-mismatch Confirmation) instead of the original single-doc +10 bump,
            // which raised one document past its pending and left adjusted != payment unanswered.
            //   MANDATORY: restore every tag (no popup) + decrease-then-Update-payment + Advance edit.
            //   PHASE1: decrease-then-adjust-more; PHASE2: increase within pending; PHASE3: over pending.
            if (ApVoucherTagStateClaimSupport.matchesGroup(activeGroup, ApVoucherExecutionGroup.MANDATORY)) {
                results.add(resolveDocumentAdjustmentCandidate(jdbi, ApVoucherEditScenarioType.DETAIL_MULTI_DOC_RESTORE));
                results.add(resolveDocumentAdjustmentCandidate(jdbi, ApVoucherEditScenarioType.DETAIL_AMOUNT_DOC_DECREASE_UPDATE_PAYMENT));
                results.add(resolveDetailLineAmountCandidate(jdbi, ApVoucherEditScenarioType.DETAIL_AMOUNT_ADVANCE));
            }
            if (ApVoucherTagStateClaimSupport.matchesGroup(activeGroup, ApVoucherExecutionGroup.REGRESSION_PHASE1)) {
                results.add(resolveDocumentAdjustmentCandidate(jdbi, ApVoucherEditScenarioType.DETAIL_AMOUNT_DOC_DECREASE_ADJUST_MORE));
            }
            if (ApVoucherTagStateClaimSupport.matchesGroup(activeGroup, ApVoucherExecutionGroup.REGRESSION_PHASE2)) {
                results.add(resolveDocumentAdjustmentCandidate(jdbi, ApVoucherEditScenarioType.DETAIL_AMOUNT_DOC_INCREASE_WITHIN_PENDING));
            }
            if (ApVoucherTagStateClaimSupport.matchesGroup(activeGroup, ApVoucherExecutionGroup.REGRESSION_PHASE3)) {
                results.add(resolveDocumentAdjustmentCandidate(jdbi, ApVoucherEditScenarioType.DETAIL_AMOUNT_DOC_OVER_PENDING_NEGATIVE));
            }
        } else if ("editApVoucherPaymentInfo".equals(methodName)) {
            // MANDATORY only — combination #12 (Payment Info change only).
            if (ApVoucherTagStateClaimSupport.matchesGroup(activeGroup, ApVoucherExecutionGroup.MANDATORY)) {
                results.add(resolvePaymentInfoCandidate(jdbi));
            }
        } else if (ApVoucherTagStateClaimSupport.matchesGroup(activeGroup, ApVoucherExecutionGroup.MANDATORY)) {
            results.add(resolveOneCandidate(jdbi, ApVoucherTagState.UNTAGGED));
            results.add(resolveOneCandidate(jdbi, ApVoucherTagState.TAGGED));
        }

        TestDataSource.stampInvocations(results);
        int total = results.size();

        Log.info("[DB] ApVoucherEdit emitted {} row(s) for '{}' — group filter={}",
                results.size(), methodName, activeGroup);
        return results;
    }

    private static MergedFinanceApVoucherData resolveOneCandidate(Jdbi jdbi, ApVoucherTagState tagState) {
        List<String> gatheringLog = new ArrayList<>();
        gatheringLog.add("═══════════════════════════════════════════════════════════");
        gatheringLog.add("  DB DATA GATHERING — AP VOUCHER CLAIM (EDIT, tagState=" + tagState + ")");
        gatheringLog.add("═══════════════════════════════════════════════════════════");

        DbQueryBuffer.start();
        FinVchMain header;
        try {
            header = DataAvailabilityGuard.selectOrBackfill(
                    () -> ApVoucherTagStateClaimSupport.claimOne(jdbi, tagState)
                            .orElseThrow(() -> new SkipException(
                                    "No documents found for the criteria: vchtype='P', release_status='P', "
                                    + "tagState=" + tagState + ", unclaimed this run — need 1.")),
                    1,
                    ApVoucherTagStateClaimSupport.backfillStrategyFor(jdbi, tagState));
        } finally {
            List<String> executedQueries = DbQueryBuffer.flush();
            if (!executedQueries.isEmpty()) {
                gatheringLog.add("");
                gatheringLog.add("QUERIES EXECUTED DURING DATA GATHERING");
                gatheringLog.addAll(executedQueries);
            }
        }

        gatheringLog.add("  Claimed voucher scheme_docno='" + header.getSchemeDocno()
                + "' (vchcode=" + header.getVchcode() + "), release_status='" + header.getReleaseStatus() + "'");
        gatheringLog.add("═══════════════════════════════════════════════════════════");

        // Date is left UNCHANGED (the voucher's own existing vchdt), not freshly resolved — AP
        // Voucher's Date field is genuinely disabled on Edit by business rule (confirmed live DOM,
        // run 20260930_012130: disabled="" aria-disabled="true"), so attempting to change it here
        // was always guaranteed to fail. setDocumentDate()'s own equals-guard (current == date →
        // skip) means passing the same value back never touches the field at all — same
        // "unchanged-on-Edit" convention this class already uses for voucherno/cashbank below. The
        // negative case that asserts the field IS disabled lives in editApVoucherDateFieldDisabled
        // instead (see this data source's own methodName branch further down).
        DocumentInformation docInfo = new DocumentInformation(
                header.getSchemeDocno(), // testCaseId — reuse scheme_docno for traceability
                null,                    // ownersite — unchanged on Edit
                null,                    // voucherno — left null, see class Javadoc
                null,                    // cashbank — unchanged on Edit
                FakerUtils.randomRemarks(),
                header.getVchdt(),       // date — unchanged (see comment above)
                FakerUtils.randomReference(),
                null);                   // onAccount — resolved live by EditApVoucher itself

        return MergedFinanceApVoucherData.builder()
                .existingVoucherNo(header.getSchemeDocno())
                .releaseStatus(header.getReleaseStatus())
                .documentInformation(docInfo)
                .dbGatheringLog(gatheringLog)
                .invocationIndex(1)
                .totalInvocations(1)
                .build();
    }

    /**
     * Claims a candidate for {@code editApVoucherDetailLine}'s two MANDATORY-tier scenarios —
     * {@link ApVoucherEditScenarioType#DETAIL_AMOUNT_DOCUMENT} (TAGGED, Document mode — the amount
     * change happens via re-adjustment) or {@link ApVoucherEditScenarioType#DETAIL_AMOUNT_ADVANCE}
     * (UNTAGGED, Advance mode — a plain field edit). Unlike {@link #resolveOneCandidate}, this
     * claims directly (no {@code DataAvailabilityGuard.selectOrBackfill} wrapper) — the existing
     * backfill strategies ({@code ApVoucherTaggedBackfillStrategy}/
     * {@code ApVoucherUntaggedBackfillStrategy}) don't account for the Advance-vs-Document mode
     * filter this method's own {@link ApVoucherTagStateClaimSupport#claimOne(Jdbi, ApVoucherTagState,
     * Boolean)} overload adds, so silently falling back to either would risk claiming the WRONG
     * mode without anyone noticing. A genuine shortfall here throws {@link SkipException}, same
     * honest-signal convention {@code ApVoucherTaggedBackfillStrategy} already established, not a
     * guessed backfill.
     */
    private static MergedFinanceApVoucherData resolveDetailLineAmountCandidate(
            Jdbi jdbi, ApVoucherEditScenarioType scenarioType) {
        boolean isDocumentMode = scenarioType == ApVoucherEditScenarioType.DETAIL_AMOUNT_DOCUMENT;
        ApVoucherTagState tagState = isDocumentMode ? ApVoucherTagState.TAGGED : ApVoucherTagState.UNTAGGED;
        boolean onAccountYes = !isDocumentMode;

        List<String> gatheringLog = new ArrayList<>();
        gatheringLog.add("═══════════════════════════════════════════════════════════");
        gatheringLog.add("  DB DATA GATHERING — AP VOUCHER CLAIM (EDIT DETAIL LINE, scenario=" + scenarioType + ")");
        gatheringLog.add("═══════════════════════════════════════════════════════════");

        DbQueryBuffer.start();
        FinVchMain header;
        List<FinVchDet> detLines;
        try {
            header = ApVoucherTagStateClaimSupport.claimOne(jdbi, tagState, onAccountYes)
                    .orElseThrow(() -> new SkipException(
                            "No documents found for the criteria: vchtype='P', release_status='P', "
                            + "tagState=" + tagState + ", isadvance='" + (onAccountYes ? "Y" : "N")
                            + "', unclaimed this run — need 1 (no mode-aware backfill built yet)."));
            detLines = jdbi.onDemand(FinVchDetDao.class).findByVoucherCode(header.getVchcode());
        } finally {
            List<String> executedQueries = DbQueryBuffer.flush();
            if (!executedQueries.isEmpty()) {
                gatheringLog.add("");
                gatheringLog.add("QUERIES EXECUTED DURING DATA GATHERING");
                gatheringLog.addAll(executedQueries);
            }
        }
        if (detLines.isEmpty()) {
            throw new IllegalStateException("[DetailLineAmount] finvchdet has no lines for scheme_docno="
                    + header.getSchemeDocno() + " — cannot resolve the row's own current amount.");
        }
        BigDecimal currentAmount = detLines.get(0).getDamount() != null && detLines.get(0).getDamount().signum() != 0
                ? detLines.get(0).getDamount() : detLines.get(0).getCamount();
        // Deterministic bump (not random) — the exact new value only needs to differ from the
        // current one and stay positive; this round's own DB validation asserts against whatever
        // value is set here, so a fixed, predictable delta is preferable to a random one.
        BigDecimal newAmount = currentAmount.add(BigDecimal.TEN);

        gatheringLog.add("  Claimed voucher scheme_docno='" + header.getSchemeDocno()
                + "' (vchcode=" + header.getVchcode() + "), current amount=" + currentAmount
                + ", new amount=" + newAmount);
        gatheringLog.add("═══════════════════════════════════════════════════════════");

        ApVoucherDetailLineEditTarget target = ApVoucherDetailLineEditTarget.builder()
                .lineNo("1")
                .newAmount(newAmount)
                .shape(isDocumentMode
                        ? ApVoucherDetailLineEditTarget.EditShape.ADJUSTMENT_AMOUNT_CHANGE
                        : ApVoucherDetailLineEditTarget.EditShape.AMOUNT_ONLY)
                .build();

        DocumentInformation docInfo = new DocumentInformation(
                header.getSchemeDocno(), null, null, null,
                FakerUtils.randomRemarks(), header.getVchdt(), FakerUtils.randomReference(), null);

        return MergedFinanceApVoucherData.builder()
                .existingVoucherNo(header.getSchemeDocno())
                .releaseStatus(header.getReleaseStatus())
                .documentInformation(docInfo)
                .editScenarioType(scenarioType)
                .editedDetailLine(target)
                .dbGatheringLog(gatheringLog)
                .invocationIndex(1)
                .totalInvocations(1)
                .build();
    }

    /**
     * Claims a TAGGED, Document-mode (On Account = No) Posted AP Voucher for one of the multi-document
     * adjustment scenarios and scripts its target from the voucher's OWN live tags (captured here via
     * {@link VoucherEditFlowSteps#captureTags}, at data-gather time while the tags still exist):
     * <ul>
     *   <li>{@code DETAIL_MULTI_DOC_RESTORE} — no target; every tag is simply re-applied.</li>
     *   <li>{@code ..._DECREASE_UPDATE_PAYMENT} — first document's amount lowered by a small delta
     *       (always within its pending), total &lt; payment → "Update payment amount".</li>
     *   <li>{@code ..._DECREASE_ADJUST_MORE} — same decrease, then "I will adjust more" adds another
     *       pending document of the same sub-ledger for the delta, total back to payment.</li>
     *   <li>{@code ..._INCREASE_WITHIN_PENDING} — first document raised by the delta if its pending
     *       (excluding this tag) has room, total &gt; payment → "Update payment amount".</li>
     *   <li>{@code ..._OVER_PENDING_NEGATIVE} — exploratory: amount far above pending.</li>
     * </ul>
     * Claims directly (no backfill wrapper): no captured "create + tag" contract exists, so a shortage
     * is a {@link SkipException}, the same honest signal {@code ApVoucherTaggedBackfillStrategy} uses.
     */
    private static MergedFinanceApVoucherData resolveDocumentAdjustmentCandidate(
            Jdbi jdbi, ApVoucherEditScenarioType scenarioType) {
        List<String> gatheringLog = new ArrayList<>();
        gatheringLog.add("═══════════════════════════════════════════════════════════");
        gatheringLog.add("  DB DATA GATHERING — AP VOUCHER CLAIM (EDIT DOCUMENT ADJUSTMENT, scenario=" + scenarioType + ")");
        gatheringLog.add("═══════════════════════════════════════════════════════════");

        DbQueryBuffer.start();
        FinVchMain header;
        List<FinVchDet> detLines;
        List<com.gsl.testdata.Finance.Operations.Vouchers.Common.CapturedTag> tags;
        try {
            header = ApVoucherTagStateClaimSupport.claimOne(jdbi, ApVoucherTagState.TAGGED, false)
                    .orElseThrow(() -> new SkipException(
                            "No documents found for the criteria: vchtype='P', release_status='P', tagState=TAGGED, "
                            + "isadvance='N', unclaimed this run — need 1 (tagged documents are a finite, "
                            + "consumed resource; no create+tag backfill is captured)."));
            detLines = jdbi.onDemand(FinVchDetDao.class).findByVoucherCode(header.getVchcode());
            tags = com.gsl.flow.Finance.Operations.Common.VoucherEditFlowSteps
                    .captureTags(jdbi, header.getVchcode(), header.getSchemeDocno());
        } finally {
            List<String> executedQueries = DbQueryBuffer.flush();
            if (!executedQueries.isEmpty()) {
                gatheringLog.add("");
                gatheringLog.add("QUERIES EXECUTED DURING DATA GATHERING");
                gatheringLog.addAll(executedQueries);
            }
        }
        if (detLines.isEmpty() || tags.isEmpty()) {
            throw new SkipException("[" + scenarioType + "] claimed " + header.getSchemeDocno()
                    + " has no finvchdet lines or no captured tags — cannot script an adjustment target.");
        }
        BigDecimal total = tags.stream().map(t -> t.getAmount()).reduce(BigDecimal.ZERO, BigDecimal::add);
        com.gsl.testdata.Finance.Operations.Vouchers.Common.CapturedTag first = tags.get(0);
        BigDecimal delta = first.getAmount().divide(BigDecimal.valueOf(2), 2, java.math.RoundingMode.DOWN)
                .min(BigDecimal.ONE).max(new BigDecimal("0.01"));

        ApVoucherDetailLineEditTarget target = null;
        switch (scenarioType) {
            case DETAIL_MULTI_DOC_RESTORE:
                break;   // no target — restore only
            case DETAIL_AMOUNT_DOC_DECREASE_UPDATE_PAYMENT:
                target = ApVoucherDetailLineEditTarget.builder().lineNo("1")
                        .shape(ApVoucherDetailLineEditTarget.EditShape.ADJUSTMENT_AMOUNT_CHANGE)
                        .docTarget(new com.gsl.testdata.Finance.Operations.Vouchers.Common.AdjustmentSpec.DocAmount(
                                first.getCounterDocNo(), first.getAmount().subtract(delta)))
                        .mismatchPlan(com.gsl.testdata.Finance.Operations.Vouchers.Common.AdjustmentMismatchPlan.updatePayment())
                        .expectedPaymentAmount(total.subtract(delta))
                        .build();
                break;
            case DETAIL_AMOUNT_DOC_DECREASE_ADJUST_MORE: {
                String extraDoc = findExtraPendingDocument(jdbi, detLines.get(0).getSlcode(), tags, delta);
                target = ApVoucherDetailLineEditTarget.builder().lineNo("1")
                        .shape(ApVoucherDetailLineEditTarget.EditShape.ADJUSTMENT_AMOUNT_CHANGE)
                        .docTarget(new com.gsl.testdata.Finance.Operations.Vouchers.Common.AdjustmentSpec.DocAmount(
                                first.getCounterDocNo(), first.getAmount().subtract(delta)))
                        .mismatchPlan(com.gsl.testdata.Finance.Operations.Vouchers.Common.AdjustmentMismatchPlan.adjustMore(
                                com.gsl.testdata.Finance.Operations.Vouchers.Common.AdjustmentSpec.selectDocument(extraDoc, delta)))
                        .expectedPaymentAmount(total)
                        .build();
                break;
            }
            case DETAIL_AMOUNT_DOC_INCREASE_WITHIN_PENDING: {
                BigDecimal room = pendingOf(jdbi, detLines.get(0).getSlcode(), first.getCounterDocNo());
                if (room == null || room.compareTo(delta) < 0) {
                    throw new SkipException("[" + scenarioType + "] counter-document " + first.getCounterDocNo()
                            + " has no pending room (" + room + ") to raise its tagged amount by " + delta);
                }
                target = ApVoucherDetailLineEditTarget.builder().lineNo("1")
                        .shape(ApVoucherDetailLineEditTarget.EditShape.ADJUSTMENT_AMOUNT_CHANGE)
                        .docTarget(new com.gsl.testdata.Finance.Operations.Vouchers.Common.AdjustmentSpec.DocAmount(
                                first.getCounterDocNo(), first.getAmount().add(delta)))
                        .mismatchPlan(com.gsl.testdata.Finance.Operations.Vouchers.Common.AdjustmentMismatchPlan.updatePayment())
                        .expectedPaymentAmount(total.add(delta))
                        .build();
                break;
            }
            case DETAIL_AMOUNT_DOC_OVER_PENDING_NEGATIVE:
                target = ApVoucherDetailLineEditTarget.builder().lineNo("1")
                        .shape(ApVoucherDetailLineEditTarget.EditShape.ADJUSTMENT_AMOUNT_CHANGE)
                        .docTarget(new com.gsl.testdata.Finance.Operations.Vouchers.Common.AdjustmentSpec.DocAmount(
                                first.getCounterDocNo(), first.getAmount().add(new BigDecimal("1000000.00"))))
                        .build();   // exploratory: no plan, no expected payment — records the app's behavior
                break;
            default:
                throw new IllegalArgumentException("Not a document-adjustment edit scenario: " + scenarioType);
        }

        gatheringLog.add("  Claimed voucher scheme_docno='" + header.getSchemeDocno()
                + "' (vchcode=" + header.getVchcode() + "), " + tags.size() + " tag(s), tagged total=" + total);
        gatheringLog.add("═══════════════════════════════════════════════════════════");

        DocumentInformation docInfo = new DocumentInformation(
                header.getSchemeDocno(), null, null, null,
                FakerUtils.randomRemarks(), header.getVchdt(), FakerUtils.randomReference(), null);

        return MergedFinanceApVoucherData.builder()
                .existingVoucherNo(header.getSchemeDocno())
                .releaseStatus(header.getReleaseStatus())
                .documentInformation(docInfo)
                .editScenarioType(scenarioType)
                .editedDetailLine(target)
                .dbGatheringLog(gatheringLog)
                .invocationIndex(1)
                .totalInvocations(1)
                .build();
    }

    /** Pending amount of {@code documentNo} for the sub-ledger {@code slcode} (AP documents are
     *  {@code debit_credit='C'}); {@code null} when not found. */
    private static BigDecimal pendingOf(Jdbi jdbi, long slcode, String documentNo) {
        return jdbi.withHandle(h -> h.createQuery(
                        "SELECT pa.pending FROM v_pending_adj pa JOIN finsl fs "
                        + "ON fs.slname = pa.slname AND fs.slid = pa.slid "
                        + "WHERE fs.slcode = :slcode AND pa.document_no = :doc AND pa.debit_credit = 'C' AND pa.pending > 0")
                .bind("slcode", slcode).bind("doc", documentNo)
                .mapTo(BigDecimal.class).findFirst().orElse(null));
    }

    /** Another unreserved pending AP document of the same sub-ledger (not among the captured tags)
     *  with at least {@code minPending} pending — to be added via "I will adjust more". */
    private static String findExtraPendingDocument(Jdbi jdbi, long slcode,
            List<com.gsl.testdata.Finance.Operations.Vouchers.Common.CapturedTag> tags, BigDecimal minPending) {
        java.util.Set<String> exclude = new java.util.HashSet<>();
        tags.forEach(t -> exclude.add(t.getCounterDocNo()));
        List<String> docs = jdbi.withHandle(h -> h.createQuery(
                        "SELECT pa.document_no FROM v_pending_adj pa JOIN finsl fs "
                        + "ON fs.slname = pa.slname AND fs.slid = pa.slid "
                        + "WHERE fs.slcode = :slcode AND pa.debit_credit = 'C' AND pa.pending >= :min "
                        + "ORDER BY pa.document_date")
                .bind("slcode", slcode).bind("min", minPending)
                .mapTo(String.class).list());
        return docs.stream()
                .filter(d -> !exclude.contains(d))
                .filter(com.gsl.database.DocumentReservationRegistry::tryReserve)
                .findFirst()
                .orElseThrow(() -> new SkipException("No other pending AP document with >= " + minPending
                        + " pending exists for sub-ledger slcode=" + slcode + " to add via \"I will adjust more\"."));
    }

    /**
     * Claims a candidate for {@code editApVoucherPaymentInfo} — a Posted AP Voucher whose first
     * {@code finvchdet} row is already Cheque-mode ({@code payment_mode='C'}) with a non-null
     * {@code chqno}, mirroring {@code DbGeneralVoucherEditDataSource#buildPaymentEditTarget}'s own
     * claim shape. Deliberately simpler than GV's version (no bank-match/bookcode resolution — see
     * {@link ApVoucherPaymentInfoEditTarget}'s own class Javadoc for why): the "before" side only
     * needs its existing instrument number (for the live-DOM cross-check), and the "after" side is
     * a plain, freshly-generated instrument number, not a resolved-against-a-real-bank one.
     */
    private static MergedFinanceApVoucherData resolvePaymentInfoCandidate(Jdbi jdbi) {
        List<String> gatheringLog = new ArrayList<>();
        gatheringLog.add("═══════════════════════════════════════════════════════════");
        gatheringLog.add("  DB DATA GATHERING — AP VOUCHER CLAIM (EDIT PAYMENT INFO)");
        gatheringLog.add("═══════════════════════════════════════════════════════════");

        String sql = "SELECT m.* FROM finvchmain m "
                + "WHERE m.vchtype = 'P' AND m.release_status = 'P' "
                + "AND EXISTS (SELECT 1 FROM finvchdet d WHERE d.vchcode = m.vchcode "
                + "AND d.payment_mode = 'C' AND d.chqno IS NOT NULL) "
                + "ORDER BY m.vchcode DESC";

        DbQueryBuffer.start();
        FinVchMain header;
        List<FinVchDet> detLines;
        try {
            List<FinVchMain> candidates = jdbi.withHandle(handle ->
                    handle.createQuery(sql).mapToBean(FinVchMain.class).list());
            header = candidates.stream()
                    .filter(h -> com.gsl.database.DocumentReservationRegistry.tryReserve(String.valueOf(h.getVchcode())))
                    .findFirst()
                    .orElseThrow(() -> new SkipException(
                            "No documents found for the criteria: vchtype='P', release_status='P', "
                            + "finvchdet.payment_mode='C' AND chqno IS NOT NULL, unclaimed this run — need 1."));
            detLines = jdbi.onDemand(FinVchDetDao.class).findByVoucherCode(header.getVchcode());
        } finally {
            List<String> executedQueries = DbQueryBuffer.flush();
            if (!executedQueries.isEmpty()) {
                gatheringLog.add("");
                gatheringLog.add("QUERIES EXECUTED DURING DATA GATHERING");
                gatheringLog.addAll(executedQueries);
            }
        }
        if (detLines.isEmpty() || detLines.get(0).getChqno() == null) {
            throw new IllegalStateException("[PaymentInfo] finvchdet row 1 for scheme_docno="
                    + header.getSchemeDocno() + " unexpectedly has no chqno — claim query should have excluded this.");
        }
        String beforeInstrumentNo = detLines.get(0).getChqno();
        String newInstrumentNo = "CHQ" + FakerUtils.randomInt(100000, 999999);

        gatheringLog.add("  Claimed voucher scheme_docno='" + header.getSchemeDocno()
                + "' (vchcode=" + header.getVchcode() + "), before instrument no='" + beforeInstrumentNo
                + "', new instrument no='" + newInstrumentNo + "'");
        gatheringLog.add("═══════════════════════════════════════════════════════════");

        PaymentInformation beforePayment = new PaymentInformation(
                header.getSchemeDocno(), "1", PaymentSelectionType.CHEQUE, beforeInstrumentNo, null, null, null);
        PaymentInformation newPayment = new PaymentInformation(
                header.getSchemeDocno(), "1", PaymentSelectionType.CHEQUE, newInstrumentNo, null,
                detLines.get(0).getDrawnOn(), null);

        ApVoucherPaymentInfoEditTarget target = ApVoucherPaymentInfoEditTarget.builder()
                .lineNo("1")
                .beforePayment(beforePayment)
                .newPayment(newPayment)
                .build();

        DocumentInformation docInfo = new DocumentInformation(
                header.getSchemeDocno(), null, null, null,
                FakerUtils.randomRemarks(), header.getVchdt(), FakerUtils.randomReference(), null);

        return MergedFinanceApVoucherData.builder()
                .existingVoucherNo(header.getSchemeDocno())
                .releaseStatus(header.getReleaseStatus())
                .documentInformation(docInfo)
                .editScenarioType(ApVoucherEditScenarioType.DETAIL_PAYMENT_INFO)
                .paymentEditTarget(target)
                .dbGatheringLog(gatheringLog)
                .invocationIndex(1)
                .totalInvocations(1)
                .build();
    }
}
