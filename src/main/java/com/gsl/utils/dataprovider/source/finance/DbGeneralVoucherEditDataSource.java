package com.gsl.utils.dataprovider.source.finance;

import com.gsl.config.ConfigFactory;
import com.gsl.database.DataAvailabilityGuard;
import com.gsl.database.DatabaseManager;
import com.gsl.database.DbQueryBuffer;
import com.gsl.database.DocumentReservationRegistry;
import com.gsl.database.dao.HrEmpDao;
import com.gsl.database.dao.HrEmpOuDao;
import com.gsl.database.dao.adm.AdmSiteDao;
import com.gsl.database.dao.adm.AdmYearDao;
import com.gsl.database.dao.finance.FinCostTagDao;
import com.gsl.database.dao.finance.FinGlDao;
import com.gsl.database.dao.finance.FinGlSiteDao;
import com.gsl.database.dao.finance.FinSlDao;
import com.gsl.database.dao.finance.FinSlGlDao;
import com.gsl.database.dao.finance.FinSlGlSiteDao;
import com.gsl.database.dao.finance.FinVchDetDao;
import com.gsl.database.dao.finance.FinVchMainDao;
import com.gsl.database.finance.GeneralVoucherEditRevertBackfillStrategy;
import com.gsl.driver.DriverManager;
import com.gsl.enums.CashBankType;
import com.gsl.enums.Finance.Operations.Common.DocumentMessageBoxMessage;
import com.gsl.enums.Finance.Operations.GeneralVoucher.GlLineType;
import com.gsl.enums.GlAccountType;
import com.gsl.enums.PaymentSelectionType;
import com.gsl.enums.PeriodDomain;
import com.gsl.model.adm.AdmSite;
import com.gsl.model.adm.AdmYear;
import com.gsl.model.finance.FinCostTag;
import com.gsl.model.finance.FinGl;
import com.gsl.model.finance.FinGlSite;
import com.gsl.model.finance.FinSl;
import com.gsl.model.finance.FinSlGl;
import com.gsl.model.finance.FinSlGlSite;
import com.gsl.model.finance.FinVchDet;
import com.gsl.model.finance.FinVchMain;
import com.gsl.pages.enums.Finance.Operations.GeneralVoucher.GeneralVoucherRevertOutcome;
import com.gsl.testdata.Finance.Operations.Vouchers.GeneralVoucher.DetailLineEditTarget;
import com.gsl.testdata.Finance.Operations.Vouchers.GeneralVoucher.DocumentInformation;
import com.gsl.testdata.Finance.Operations.Vouchers.GeneralVoucher.ExpenseIncomeDetail;
import com.gsl.testdata.Finance.Operations.Vouchers.GeneralVoucher.PaymentInfoEditTarget;
import com.gsl.testdata.Finance.Operations.Vouchers.GeneralVoucher.PaymentInformation;
import com.gsl.testdata.Finance.Operations.Vouchers.GeneralVoucher.SiteAllocation;
import com.gsl.testdata.UserCredential;
import com.gsl.testdata.dto.GeneralVoucherOperationMode;
import com.gsl.testdata.dto.MergedFinanceGeneralVoucherData;
import com.gsl.utils.DateUtils;
import com.gsl.utils.Faker.FakerUtils;
import com.gsl.utils.Log;
import com.gsl.utils.cheque.ChequeInstrumentResolver;
import com.gsl.utils.dataprovider.fixture.GeneralVoucherEditFixtureFactory;
import com.gsl.utils.payment.PaymentReferenceGenerator;
import com.gsl.utils.dataprovider.fixture.finance.GeneralVoucherEditCombinationFilter;
import com.gsl.utils.dataprovider.fixture.finance.GeneralVoucherEditCombinations;
import com.gsl.utils.dataprovider.fixture.finance.GeneralVoucherEditExecutionGroup;
import com.gsl.utils.dataprovider.fixture.finance.GeneralVoucherEditVariant;
import com.gsl.utils.dataprovider.fixture.finance.NegativeValidation;
import com.gsl.utils.dataprovider.fixture.finance.PaymentInfoDateScenario;
import com.gsl.utils.dataprovider.fixture.finance.VoucherEditDateVariant;
import com.gsl.utils.dataprovider.source.TestDataSource;
import com.gsl.utils.dataprovider.source.finance.common.AccountingPeriodCache;
import com.gsl.utils.dataprovider.source.finance.common.AccountingPeriodResolver;
import org.jdbi.v3.core.Jdbi;
import org.testng.SkipException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * DB-driven test-data source for {@code editGeneralVoucherHeader}/{@code editGeneralVoucherDetailLine}/
 * {@code editGeneralVoucherPaymentInfo} — sweeps the fixed 24-scenario {@link
 * GeneralVoucherEditCombinations#allCombinations()} matrix, filtered by the {@code gv.edit.group}
 * parameter (kept deliberately distinct from Create's {@code gv.group} — see {@link
 * GeneralVoucherEditExecutionGroup}'s Javadoc), and for each surviving variant matching the
 * requested test method's scenario type (HEADER_ONLY vs DETAIL_LINE vs PAYMENT_INFO — a variant
 * whose {@link GeneralVoucherEditVariant#getScenarioType()} doesn't match the running method is
 * skipped, not emitted, since all three test methods share this one matrix):
 *
 * <ol>
 *   <li>Claims one existing General Voucher per plan §4's selection rule — see
 *       {@link #claimCandidate}.</li>
 *   <li>Resolves owner site name and Cash/Bank GL name from the claimed header.</li>
 *   <li>Resolves <b>every</b> existing {@code finvchdet} row into an {@link ExpenseIncomeDetail}
 *       (GL name, amount/side, {@code isReversal}, {@code resolvedGlType}) — not just the one
 *       being edited, so "unchanged rows stayed unchanged" has real data to diff against.</li>
 *   <li>Resolves existing {@code fincosttag} rows for each Site-applicable line into
 *       {@link SiteAllocation} objects.</li>
 *   <li>For {@code DETAIL_LINE} variants, picks the first resolved row matching the variant's
 *       {@code glLineType} as the edited line (or, when {@code requireSharedGlcodeSibling} is
 *       set, the first such row that ALSO shares its glcode with a different-slcode sibling row
 *       — see {@link #findRowWithSharedGlcodeSibling}), building a {@link DetailLineEditTarget}
 *       with a new amount (existing + a fixed, clearly-different delta), the variant's
 *       {@code reversalToggle}, and (when {@code narrationEdit} is set) a new Ledger
 *       Narration.</li>
 *   <li>Builds the header {@link DocumentInformation} with the <b>same</b> site/Cash-Bank and a
 *       <b>new</b>, accounting-year-aware date (see {@code AccountingPeriodResolver})/reference/
 *       remarks.</li>
 *   <li>Sets {@code operationMode=EDIT}, {@code existingVoucherNo}, {@code releaseStatus}.</li>
 * </ol>
 *
 * <p>Single-phase, like {@link DbGeneralVoucherRevertDataSource} — resolving a document to edit
 * needs no live-browser-dependent second phase, so the whole thing runs inside {@link #load} at
 * {@code @DataSupplier} time.
 */
public class DbGeneralVoucherEditDataSource implements TestDataSource<MergedFinanceGeneralVoucherData> {

    /** A clearly-different new amount delta applied to the edited detail line — large enough that
     *  it can never coincidentally equal the existing amount. */
    private static final BigDecimal AMOUNT_DELTA = new BigDecimal("500.00");

    private final String xmlEditGroupParam;

    /** No execution-group filter — every combination is emitted (full-suite run). */
    public DbGeneralVoucherEditDataSource() {
        this(null);
    }

    /** @param xmlEditGroupParam fallback {@code gv.edit.group} value from TestNG {@code <parameter>},
     *                           used when the {@code gv.edit.group} system property is absent
     *                           (IntelliJ direct-run path) */
    public DbGeneralVoucherEditDataSource(String xmlEditGroupParam) {
        this.xmlEditGroupParam = xmlEditGroupParam;
    }

    /**
     * Sweeps {@link GeneralVoucherEditCombinations#allCombinations()}, keeping only variants that
     * survive the active {@code gv.edit.group} filter AND whose
     * {@link GeneralVoucherEditVariant#getScenarioType()} matches {@code methodName} (the
     * 24-variant matrix is shared across all three edit test methods — see the class Javadoc),
     * resolving each surviving variant via {@link #resolveOne}.
     *
     * @throws SkipException if {@code methodName} isn't one of the three supported edit methods
     */
    @Override
    public List<MergedFinanceGeneralVoucherData> load(String methodName) {
        GeneralVoucherEditExecutionGroup activeGroup = GeneralVoucherEditCombinationFilter.resolveActiveGroup(xmlEditGroupParam);

        if (!"editGeneralVoucherHeader".equals(methodName) && !"editGeneralVoucherDetailLine".equals(methodName)
                && !"editGeneralVoucherPaymentInfo".equals(methodName)) {
            throw new SkipException("[DbGeneralVoucherEditDataSource] Unknown method '" + methodName
                    + "' — only editGeneralVoucherHeader/editGeneralVoucherDetailLine/"
                    + "editGeneralVoucherPaymentInfo are supported.");
        }

        Jdbi jdbi = DatabaseManager.getJdbi();
        // Vouchers already handed out to an earlier variant in THIS load() call — load() runs
        // single-threaded at @DataSupplier time, before TestNG's runInParallel fan-out, mirroring
        // DbGeneralVoucherRevertDataSource's own claimedThisRun idiom.
        Set<Long> claimedThisRun = new HashSet<>();

        List<MergedFinanceGeneralVoucherData> results = new ArrayList<>();
        for (GeneralVoucherEditVariant variant : GeneralVoucherEditCombinations.allCombinations()) {
            if (!GeneralVoucherEditCombinationFilter.survives(variant, activeGroup)) continue;

            if (!GeneralVoucherEditFixtureFactory.shouldRun(variant.getScenarioType(), methodName)) {
                continue; // this variant belongs to the OTHER edit method
            }

            results.add(resolveOne(jdbi, variant, claimedThisRun));
        }

        int total = results.size();
        for (int i = 0; i < total; i++) {
            results.get(i).setInvocationIndex(i + 1);
            results.get(i).setTotalInvocations(total);
        }

        Log.info("[DB] GeneralVoucherEdit emitted {} row(s) for '{}' — group filter={}",
                results.size(), methodName, activeGroup);
        return results;
    }

    /**
     * Builds one {@link MergedFinanceGeneralVoucherData} for {@code variant}: claims a matching
     * voucher (via {@link #claimCandidate}), and — unless {@code variant.isRequireClosedPeriod()}
     * short-circuits to the minimal closed-period-negative shape — resolves its site/GL, every
     * existing {@code finvchdet} row, and its site allocations, then dispatches to
     * {@link #buildEditedDetailLine} or {@link #buildPaymentEditTarget} depending on
     * {@code variant.getScenarioType()} before computing the new header date/reference/remarks
     * (see the class Javadoc's numbered "Data flow" list).
     */
    private static MergedFinanceGeneralVoucherData resolveOne(Jdbi jdbi, GeneralVoucherEditVariant variant,
            Set<Long> claimedThisRun) {

        List<String> gatheringLog = new ArrayList<>();
        gatheringLog.add("═══════════════════════════════════════════════════════════");
        gatheringLog.add("  DB DATA GATHERING — EXISTING VOUCHER CLAIM (GENERAL VOUCHER EDIT)");
        gatheringLog.add("═══════════════════════════════════════════════════════════");
        gatheringLog.add("  Variant='" + variant.getVariantId() + "', sourceStatus=" + variant.getSourceStatus()
                + ", scenarioType=" + variant.getScenarioType() + ", glLineType=" + variant.getGlLineType());

        DbQueryBuffer.start();
        Long vchcode;
        try {
            vchcode = claimCandidate(jdbi, variant, claimedThisRun, gatheringLog);
        } finally {
            List<String> executedQueries = DbQueryBuffer.flush();
            if (!executedQueries.isEmpty()) {
                gatheringLog.add("");
                gatheringLog.add("───────────────────────────────────────────────────────────");
                gatheringLog.add("  QUERIES EXECUTED DURING DATA GATHERING");
                gatheringLog.add("───────────────────────────────────────────────────────────");
                gatheringLog.addAll(executedQueries);
            }
        }

        FinVchMain header = jdbi.onDemand(FinVchMainDao.class).findByVoucherCode(vchcode)
                .orElseThrow(() -> new IllegalStateException(
                        "[DbGeneralVoucherEditDataSource] Claimed vchcode=" + vchcode
                        + " vanished before its header could be re-read."));

        gatheringLog.add("  Claimed voucher scheme_docno='" + header.getSchemeDocno() + "' (vchcode=" + vchcode
                + "), release_status='" + header.getReleaseStatus() + "'");

        if (variant.isRequireClosedPeriod()) {
            // GV_EDIT_HEADER_POSTED_MONTH_CLOSED_NEGATIVE: this candidate's own accounting
            // year/financial month is CONFIRMED closed (claimOne's inverted guard) — the live app
            // refuses to even Unpost it, so EditGeneralVoucher never reaches detail-row resolution,
            // date computation, or Save at all. Build the minimal row that scenario needs (mirrors
            // DbGeneralVoucherRevertDataSource#resolveClosedPeriodNegativeCandidate's own minimal
            // shape) rather than resolving the full detail matrix this variant will never use.
            gatheringLog.add("  Own accounting year/financial month CONFIRMED closed; expecting "
                    + "\"Unpost if Posted\" to be REJECTED with: \""
                    + DocumentMessageBoxMessage.REVERT_MONTH_ALREADY_CLOSED.getText() + "\"");
            gatheringLog.add("═══════════════════════════════════════════════════════════");

            // documentInformation must be non-null even though this scenario never opens the UI
            // form — EditGeneralVoucher.runEditFlow calls GeneralVoucherExtentDescriptionLogger
            // .log(data) unconditionally at the very top, before Step 0, and that shared AR/AP/GV
            // base class (VoucherExtentDescriptionLogger) reads
            // data.getDocumentInformation().getDocumentdate() with NO null check (confirmed live,
            // report 20260831_163724: FIN-GV-EDIT-HEADER-05 NullPointerException). Built straight
            // from the header's own current values — no DB lookups needed since nothing downstream
            // reads ownersite/cashbank for this early-return scenario.
            DocumentInformation minimalDocInfo = new DocumentInformation(
                    variant.getVariantId(), header.getSchemeDocno(), header.getVchdt(),
                    header.getRefno(), null, header.getNartext());

            return MergedFinanceGeneralVoucherData.builder()
                    .scenarioTypeLabel(variant.getVariantId())
                    .existingVoucherNo(header.getSchemeDocno())
                    .releaseStatus(header.getReleaseStatus())
                    .currentDocDate(header.getVchdt())
                    .documentInformation(minimalDocInfo)
                    .reservedDocumentKey(String.valueOf(vchcode))
                    .operationMode(GeneralVoucherOperationMode.EDIT)
                    .expenseIncomeDetailList(List.of())
                    .siteAllocationList(List.of())
                    .entryMethod(variant.getEntryMethod())
                    .expectedRevertOutcome(GeneralVoucherRevertOutcome.CANNOT_REVERT_MONTH_CLOSED)
                    .dbGatheringLog(gatheringLog)
                    .invocationIndex(1)
                    .totalInvocations(1)
                    .build();
        }

        String siteName = jdbi.withHandle(h ->
                h.createQuery("SELECT name FROM admsite WHERE code = :code")
                        .bind("code", header.getAdmsiteCodeOwner())
                        .mapTo(String.class)
                        .findFirst()
                        .orElse(null));

        FinGlDao glDao = jdbi.onDemand(FinGlDao.class);
        FinSlDao slDao = jdbi.onDemand(FinSlDao.class);
        FinGl cashBankGl = glDao.findByGlCode(header.getGlcode());
        String cashBankName = cashBankGl != null ? cashBankGl.getGlname() : null;
        gatheringLog.add("  Owner site='" + siteName + "', Cash/Bank GL='" + cashBankName + "'");

        List<FinVchDet> detailRows = jdbi.onDemand(FinVchDetDao.class).findByVoucherCode(vchcode);
        List<ExpenseIncomeDetail> expenseIncomeRows = resolveExpenseIncomeRows(detailRows, glDao, slDao);
        List<SiteAllocation> siteAllocations = resolveSiteAllocations(jdbi, vchcode, detailRows, expenseIncomeRows);

        gatheringLog.add("  Resolved " + expenseIncomeRows.size() + " existing finvchdet row(s), "
                + siteAllocations.size() + " fincosttag-derived site allocation(s)");

        DetailLineEditTarget editedDetailLine = null;
        if (variant.getScenarioType() == GeneralVoucherEditVariant.ScenarioType.DETAIL_LINE) {
            editedDetailLine = buildEditedDetailLine(jdbi, variant, expenseIncomeRows, siteAllocations, gatheringLog);
        }

        PaymentInfoEditTarget paymentEditTarget = null;
        if (variant.getScenarioType() == GeneralVoucherEditVariant.ScenarioType.PAYMENT_INFO) {
            paymentEditTarget = buildPaymentEditTarget(jdbi, variant, header, detailRows, gatheringLog);
        }

        // Scope fix: a DETAIL_LINE/PAYMENT_INFO scenario must edit ONLY its own target — the
        // header's date/reference/remarks stay exactly as they were before this Save. Generating a
        // new date/reference/remarks unconditionally here (the former behavior) silently turned
        // every "detail-only"/"payment-only" scenario into a combined header+other edit, which is
        // exactly the scope-ambiguity the report review flagged. That combined-edit shape is real
        // and will be covered later by its own regression-phase scenarios — not folded invisibly
        // into this one.
        boolean headerFieldsUnchanged = variant.getScenarioType() == GeneralVoucherEditVariant.ScenarioType.DETAIL_LINE
                || variant.getScenarioType() == GeneralVoucherEditVariant.ScenarioType.PAYMENT_INFO;

        LocalDate newDate;
        String newReference;
        String newRemarks;
        NegativeValidation expectedValidation;

        if (headerFieldsUnchanged) {
            newDate = header.getVchdt();
            newReference = header.getRefno();
            newRemarks = header.getNartext();
            // PaymentInfoDateScenario#expectedRejection() is null for every ordinary Payment-Info
            // variant (unchanged default) and for the 3 positive date scenarios — only
            // NEFT_FUTURE_DATE_CR_REJECTED carries a real NegativeValidation. Mirrors exactly how
            // the HEADER_ONLY branch below derives its own expectedValidation from its own flags.
            expectedValidation = variant.getDateScenario() != null
                    ? variant.getDateScenario().expectedRejection() : null;
            gatheringLog.add("  " + variant.getScenarioType() + " scenario — header date/reference/remarks "
                    + "left unchanged (date=" + newDate + ", refno='" + newReference + "')");
        } else {
            // Field-isolated header edits (plan §4D): a HEADER_ONLY variant's scope decides which
            // of date/reference/remarks actually change — ALL (the pre-existing, still-default
            // behavior) changes every field; a single-field scope holds the other two at the
            // header's own existing value, the same "hold unchanged" idiom the DETAIL_LINE branch
            // above already uses for the fields a given scenario doesn't touch. This is what makes
            // a report row actually isolate one field's effect instead of always changing all three
            // together, which made it impossible to tell which field's change a given assertion was
            // really exercising.
            GeneralVoucherEditVariant.HeaderFieldScope scope = variant.getHeaderFieldScope() != null
                    ? variant.getHeaderFieldScope() : GeneralVoucherEditVariant.HeaderFieldScope.ALL;
            boolean changeDate = scope == GeneralVoucherEditVariant.HeaderFieldScope.ALL
                    || scope == GeneralVoucherEditVariant.HeaderFieldScope.DATE_ONLY;
            boolean changeReference = scope == GeneralVoucherEditVariant.HeaderFieldScope.ALL
                    || scope == GeneralVoucherEditVariant.HeaderFieldScope.REFERENCE_ONLY;
            boolean changeRemarks = scope == GeneralVoucherEditVariant.HeaderFieldScope.ALL
                    || scope == GeneralVoucherEditVariant.HeaderFieldScope.REMARKS_ONLY;

            VoucherEditDateVariant dateVariant = null;
            if (changeDate) {
                // Accounting-year-aware new document date (replaces the former unconditional
                // LocalDate.now(), which caused a live save failure once a claimed voucher's own
                // accounting year didn't bracket today — see the "GV Edit: accounting-year-aware
                // dates..." plan). HEADER_ONLY variants always set an explicit dateVariant.
                dateVariant = variant.getDateVariant() != null
                        ? variant.getDateVariant() : VoucherEditDateVariant.RANDOM_MID;
                long ouCode = AccountingPeriodResolver.resolveOuCode(jdbi, header.getAdmsiteCodeOwner());
                newDate = AccountingPeriodResolver.resolveEditDate(
                        jdbi, header.getYcode(), ouCode, dateVariant, PeriodDomain.FINANCE);
            } else {
                newDate = header.getVchdt();
            }
            newReference = changeReference ? FakerUtils.randomReference() : header.getRefno();
            newRemarks = changeRemarks ? FakerUtils.randomRemarks() : header.getNartext();

            // Non-null for CROSS_YEAR_INVALID (always) and FUTURE_DATE when the claimed
            // candidate's Cash/Bank ledger is Cash-type (via requiredCashBankType, set only by
            // GV_EDIT_HEADER_UNPOSTED_FUTURE_DATE_CASH_NEGATIVE — see claimOne's cashBankFilter
            // above, which guarantees this variant only ever claims a Cash-type candidate) — the
            // app is expected to REJECT the save with this exact message rather than succeed (see
            // EditGeneralVoucher's Step 9b). FUTURE_DATE against a Bank-type ledger
            // (GV_EDIT_HEADER_UNPOSTED_FUTURE_DATE_BANK) is NOT a rejection case — confirmed live
            // the app accepts a future-dated document for Bank-type within the same accounting
            // year, so it falls through to `default -> null` and flows through Step 9b's normal
            // positive save + full snapshot-validation path. Only reachable when changeDate is
            // true — a scope that holds the date unchanged can never be rejected on
            // accounting-year or post-dated-Cash grounds. See "Round 22" of the GV plan
            // (FIN-GV-EDIT-HEADER-04) — this was previously a single ambiguous FUTURE_DATE
            // variant with no Cash/Bank-type awareness at all.
            expectedValidation = changeDate ? switch (dateVariant) {
                case CROSS_YEAR_INVALID -> NegativeValidation.GV_EDIT_DATE_CROSSES_ACCOUNTING_YEAR;
                case FUTURE_DATE -> variant.getRequiredCashBankType() == CashBankType.CASH
                        ? NegativeValidation.GV_CASH_POST_DATED_NOT_ALLOWED : null;
                default -> null;
            } : null;

            gatheringLog.add("  Header field scope=" + scope + " — date="
                    + (changeDate ? newDate + " (dateVariant=" + dateVariant
                        + (expectedValidation != null ? ", expecting REJECTION: " + expectedValidation.expectedMessage()
                           : dateVariant == VoucherEditDateVariant.FUTURE_DATE
                                ? ", intentionally future-dated (Cash/Bank type=" + variant.getRequiredCashBankType()
                                  + ") — app is confirmed to ACCEPT this, full snapshot validation applies "
                                  + "same as any other positive header edit"
                                : "") + ")"
                       : newDate + " (unchanged)")
                    + ", reference=" + (changeReference ? "'" + newReference + "' (new)" : "'" + newReference + "' (unchanged)")
                    + ", remarks=" + (changeRemarks ? "(new)" : "(unchanged)"));
        }

        DocumentInformation newDocInfo = new DocumentInformation(
                variant.getVariantId(),
                null,                               // voucherno — left null so setDocScheme()'s blank-guard
                                                     // never touches/reads the Voucher No. field at all (see
                                                     // plan §3: passing the existing scheme_docno here timed
                                                     // out on a live run — that field's read-before-skip path
                                                     // requires visibility that Edit mode's disabled/
                                                     // differently-rendered Voucher No. widget doesn't give
                                                     // within the wait window).
                newDate,                            // documentdate — NEW value for HEADER_ONLY, UNCHANGED for DETAIL_LINE
                newReference,                       // reference — NEW value for HEADER_ONLY, UNCHANGED for DETAIL_LINE
                cashBankName,                      // unchanged Cash/Bank
                newRemarks);                        // remarks — NEW value for HEADER_ONLY, UNCHANGED for DETAIL_LINE
        newDocInfo.setOwnersite(siteName);

        gatheringLog.add("═══════════════════════════════════════════════════════════");

        MergedFinanceGeneralVoucherData data = MergedFinanceGeneralVoucherData.builder()
                .scenarioTypeLabel(variant.getVariantId())
                .existingVoucherNo(header.getSchemeDocno())
                .releaseStatus(header.getReleaseStatus())
                .currentDocDate(header.getVchdt())
                .originalReference(header.getRefno())
                .originalRemarks(header.getNartext())
                .reservedDocumentKey(String.valueOf(vchcode))
                .operationMode(GeneralVoucherOperationMode.EDIT)
                .documentInformation(newDocInfo)
                .expenseIncomeDetailList(expenseIncomeRows)
                .siteAllocationList(siteAllocations)
                .editedDetailLine(editedDetailLine)
                .paymentEditTarget(paymentEditTarget)
                // Cross-dependency with the shared Extent header (GeneralVoucherExtentDescriptionLogger
                // #appendExtraHeaderRows): setting these here — not just paymentEditTarget — is what
                // makes the "Cheque No."/"Cheque Known/Unknown" header rows render the Edit "after"
                // state automatically, with zero further changes needed in that reporting file.
                .cashBankPaymentInfo(paymentEditTarget != null ? paymentEditTarget.getNewPayment() : null)
                .actualChequeBankMatch(paymentEditTarget != null ? paymentEditTarget.getNewBankMatch() : null)
                .headerFieldScope(variant.getHeaderFieldScope())
                .entryMethod(variant.getEntryMethod())
                .expectedValidation(expectedValidation)
                .dbGatheringLog(gatheringLog)
                .invocationIndex(1)
                .totalInvocations(1)
                .build();

        Log.info("[DB-Edit] Built edit data for vchcode={}, scheme_docno='{}', variant='{}'",
                vchcode, header.getSchemeDocno(), variant.getVariantId());
        return data;
    }

    /** Resolves every {@code finvchdet} row on the claimed voucher (not just the one being
     *  edited) into an {@link ExpenseIncomeDetail} — GL name, sub-ledger name/id, amount/side,
     *  reversal flag, and (best-effort — see the inline comments below) resolved GL type/account
     *  class. */
    private static List<ExpenseIncomeDetail> resolveExpenseIncomeRows(
            List<FinVchDet> detailRows, FinGlDao glDao, FinSlDao slDao) {
        List<ExpenseIncomeDetail> rows = new ArrayList<>();
        // Finding D (Round 12 continued): local per-call cache — a multi-line voucher with several
        // finvchdet rows sharing the same glcode/slcode (common; confirmed live: a 13-line voucher
        // sharing one glcode issued 13 identical `fingl WHERE glcode=?` queries with zero
        // memoization) used to re-issue the identical lookup once per line. Scoped to this one
        // call only — not shared across separate claimed vouchers.
        Map<Long, FinGl> glCache = new HashMap<>();
        Map<Long, FinSl> slCache = new HashMap<>();
        for (int i = 0; i < detailRows.size(); i++) {
            FinVchDet d = detailRows.get(i);
            FinGl gl = glCache.computeIfAbsent(d.getGlcode(), glDao::findByGlCode);
            String glName = gl != null ? gl.getGlname() : ("GL-" + d.getGlcode());
            boolean debit = d.getDamount() != null && d.getDamount().signum() != 0;

            // Resolves the row's sub-ledger identity (name/id) from its already-fetched
            // finvchdet.slcode via the same reverse lookup used elsewhere (FinSlDao.findBySlcode's
            // own Javadoc: "used by Edit/Delete DataSources to resolve slname + slid from a
            // finvchdet.slcode value") — previously dropped entirely (null, null passed below),
            // silently losing sub-ledger identity for every DB-driven Edit scenario before it
            // ever reached GeneralVoucherEditChangeSets' row-scoped override resolution.
            String subLedgerName = null;
            String subLedgerId = null;
            if (d.getSlcode() != 0) {
                FinSl sl = slCache.computeIfAbsent(d.getSlcode(), slDao::findBySlcode);
                if (sl != null) {
                    subLedgerName = sl.getSlname();
                    subLedgerId = sl.getSlid();
                }
            }

            ExpenseIncomeDetail row = new ExpenseIncomeDetail(
                    null,
                    String.valueOf(i + 1),
                    glName,
                    subLedgerName,
                    subLedgerId,
                    d.getNartext(),
                    "Y".equalsIgnoreCase(d.getIsReversal()) ? "Yes" : "No",
                    debit ? d.getDamount() : null,
                    debit ? null : d.getCamount());
            if (gl != null) {
                // GlLineType.fromFlags() throws for the 4th, unmodeled flag combination
                // (Sub Ledger Applicable='Y', Site Applicable='N') — confirmed live to actually
                // occur on real voucher data (e.g. glcode 1022 'INPUT CESS' in idealdb), despite
                // that method's own Javadoc assuming it "never exercised by a real General
                // Voucher". This method resolves EVERY existing finvchdet row on the claimed
                // voucher, not just the one being edited (see class Javadoc) — so a single
                // unrelated, untouched row with this combo must not crash the whole data load.
                // Leaving resolvedGlType null is already the safe/no-op case elsewhere
                // (resolveSiteAllocations skips a row with a null resolvedGlType), and this row
                // still resolves everything else (name, amount, narration, reversal) correctly —
                // it just can't be chosen as a DETAIL_LINE variant's edited target, which is
                // correct since this combination isn't a supported editable GL type.
                try {
                    row.setResolvedGlType(GlLineType.fromFlags(gl.isSiteApplicable(), gl.isSubLedgerApplicable()));
                } catch (IllegalArgumentException e) {
                    Log.warn("[DbGeneralVoucherEditDataSource] LineNo='{}' glcode={} ('{}') has an unmodeled "
                            + "GlLineType flag combination — leaving resolvedGlType null for this row rather "
                            + "than failing the whole data load. {}", row.getLineNo(), d.getGlcode(), glName, e.getMessage());
                }
                // Plan §4A: the GL's own account class (Asset/Liability/Income/Expense), resolved
                // for EVERY row (not just an edited one) so a GL-type-transition report can state
                // both the before AND after account class — same defensive try/catch idiom as
                // resolvedGlType above, since a bad/unset fingl.type must not crash the whole load.
                try {
                    row.setResolvedAccountClass(gl.accountType());
                } catch (IllegalArgumentException e) {
                    Log.warn("[DbGeneralVoucherEditDataSource] LineNo='{}' glcode={} ('{}') has an unrecognized "
                            + "fingl.type — leaving resolvedAccountClass null. {}",
                            row.getLineNo(), d.getGlcode(), glName, e.getMessage());
                }
            }
            rows.add(row);
        }
        return rows;
    }

    /** Resolves {@code fincosttag} rows into {@link SiteAllocation}s for every Site-applicable
     *  row in {@code expenseIncomeRows} (a row whose {@code resolvedGlType} is null or not
     *  site-applicable is skipped). */
    private static List<SiteAllocation> resolveSiteAllocations(Jdbi jdbi, long vchcode,
            List<FinVchDet> detailRows, List<ExpenseIncomeDetail> expenseIncomeRows) {
        List<SiteAllocation> allocations = new ArrayList<>();
        FinCostTagDao costTagDao = jdbi.onDemand(FinCostTagDao.class);
        AdmSiteDao admSiteDao = jdbi.onDemand(AdmSiteDao.class);
        String entcode = String.valueOf(vchcode);

        for (int i = 0; i < detailRows.size(); i++) {
            ExpenseIncomeDetail row = expenseIncomeRows.get(i);
            if (row.getResolvedGlType() == null || !row.getResolvedGlType().isSiteApplicable()) continue;

            List<FinCostTag> tags = costTagDao.findByEntcodeAndGlcode(entcode, detailRows.get(i).getGlcode());
            for (FinCostTag tag : tags) {
                AdmSite site = admSiteDao.findByCode(tag.getRefAdmsiteCode());
                BigDecimal amount = tag.getDamount() != null && tag.getDamount().signum() != 0
                        ? tag.getDamount() : tag.getCamount();
                SiteAllocation allocation = new SiteAllocation();
                allocation.setLineNo(row.getLineNo());
                allocation.setSiteName(site != null ? site.getName() : ("SITE-" + tag.getRefAdmsiteCode()));
                allocation.setAmount(amount);
                allocations.add(allocation);
            }
        }
        return allocations;
    }

    /**
     * Builds the before/after Payment Information pair for a PAYMENT_INFO scenario — reads the
     * claimed voucher's FIRST finvchdet row (the single counter-leg row every GV Cash/Bank
     * payment concept lives on — see {@link PaymentInformation}'s own Javadoc) for the "before"
     * state, then resolves a fresh known-cheque instrument number for the SAME Cash/Bank glcode
     * via {@link ChequeInstrumentResolver} — mirroring {@code GeneralVoucherTemplateLoader}'s
     * Create-flow cheque resolution, just applied to an EXISTING voucher instead of a brand-new
     * one. {@code claimOne}'s own filter guarantees {@code detailRows.get(0)} is already
     * Cheque-mode with a non-null {@code chqno}, so {@code beforeMode} resolves to {@code CHEQUE}
     * here in practice — {@link PaymentSelectionType#fromDbCode} is still a genuine read of the
     * live DB value, not an assumption.
     */
    private static PaymentInfoEditTarget buildPaymentEditTarget(Jdbi jdbi, GeneralVoucherEditVariant variant,
            FinVchMain header, List<FinVchDet> detailRows, List<String> gatheringLog) {

        if (detailRows.isEmpty()) {
            throw new SkipException("Skipped due to lack of data: claimed voucher has no finvchdet rows "
                    + "at all, for variant '" + variant.getVariantId() + "'.");
        }
        FinVchDet firstRow = detailRows.get(0);

        PaymentSelectionType beforeMode = PaymentSelectionType.fromDbCode(firstRow.getPaymentMode());
        PaymentInformation beforePayment = new PaymentInformation(
                variant.getVariantId(), "CASHBANK", beforeMode, firstRow.getChqno(),
                firstRow.getChqdt() != null ? firstRow.getChqdt().toLocalDate().toString() : null,
                firstRow.getDrawnOn(), null);

        PaymentInfoEditTarget.PaymentModeShape shape = variant.getPaymentInfoEditShape() != null
                ? variant.getPaymentInfoEditShape() : PaymentInfoEditTarget.PaymentModeShape.SAME_MODE_NEW_INSTRUMENT;

        // futureInstrumentDate is non-null only for the 4 dedicated PaymentInfoDateScenario
        // regression variants — resolved once, up front, since both branches below may need it.
        String futureInstrumentDate = variant.getDateScenario() != null && variant.getDateScenario().futureDate()
                ? resolveFutureInstrumentDate(jdbi, header).format(DateUtils.UI_DATE_FORMAT)
                : null;

        PaymentInformation newPayment;
        ChequeInstrumentResolver.ChequeBankMatch newBankMatch;
        Long newBookcode;

        if (shape == PaymentInfoEditTarget.PaymentModeShape.CHEQUE_TO_NEFT) {
            // instrumentNo = a synthetic NEFT reference (the same generic "instrumentNo" slot
            // Cheque uses — finvchdet has no separate NEFT-reference column), drawnOn/reference =
            // null. instrumentDate = the voucher's OWN (unchanged) document date by default, NOT
            // LocalDate.now() — a confirmed live bug (reports/runs/20260917_005651 and
            // 20260922_225443, FIN-GV-EDIT-PAYMENT-02): PAYMENT_INFO scenarios leave the header
            // date unchanged (see headerFieldsUnchanged above), so "today" is almost always LATER
            // than a pre-existing seeded voucher's own document date, and NEFT/RTGS rejects any
            // instrument date greater than the entry date ("Instrument date cannot be greater
            // than entry date", confirmed via live screenshot) — this was failing deterministically,
            // not flakily. Create's equivalent (GeneralVoucherTemplateLoader#combinationTemplate)
            // never hit this because ITS document date is also LocalDate.now(), so the two always
            // matched there; Edit's document date is static, so it must be reused directly instead.
            // futureInstrumentDate (set only for NEFT_FUTURE_DATE_CR_REJECTED) deliberately
            // overrides this default to genuinely exercise the rejection rule.
            String instrumentDate = futureInstrumentDate != null
                    ? futureInstrumentDate : header.getVchdt().format(DateUtils.UI_DATE_FORMAT);
            newPayment = new PaymentInformation(variant.getVariantId(), "CASHBANK", PaymentSelectionType.NEFT_RTGS,
                    PaymentReferenceGenerator.generateNEFT(), instrumentDate, null, null);
            // NEFT has no cheque-book concept at all — no resolver call, no bank-match/bookcode outcome.
            newBankMatch = null;
            newBookcode = null;
        } else {
            // SAME_MODE_NEW_INSTRUMENT and NEFT_TO_CHEQUE both land here — both move TO Cheque
            // with a fresh known/same-bank instrument number; the only difference between them is
            // what the "before" row's payment_mode was, guaranteed by claimOne's shape-specific
            // filter (payment_mode='C' vs. 'R'). instrumentDate stays null (unset — matches
            // popup/UI defaults) unless futureInstrumentDate overrides it (CHEQUE_FUTURE_DATE_
            // CR_ACCEPTED/CHEQUE_FUTURE_DATE_DR_ACCEPTED).
            ChequeInstrumentResolver.ChequeResolution resolution =
                    ChequeInstrumentResolver.resolveKnownSameBank(jdbi, header.getGlcode());
            // Drawn On deliberately carries the SAME value as before — this scenario only swaps
            // the instrument number/mode, not the bank the cheque is drawn on (see
            // GeneralVoucherEditChangeSets#paymentInfoEdit's matching "no drawnon rule" note).
            newPayment = new PaymentInformation(variant.getVariantId(), "CASHBANK", PaymentSelectionType.CHEQUE,
                    resolution.chqno(), futureInstrumentDate, firstRow.getDrawnOn(), null);
            newBankMatch = resolution.bankMatch();
            newBookcode = resolution.bookcode();
        }

        gatheringLog.add("  Payment-Info edit (" + shape + "): '" + beforePayment.getPaymentMode() + " / "
                + beforePayment.getInstrumentNo() + "' -> '" + newPayment.getPaymentMode() + " / "
                + newPayment.getInstrumentNo() + "' (newBankMatch=" + newBankMatch + ", newBookcode=" + newBookcode + ")");

        return PaymentInfoEditTarget.builder()
                .beforePayment(beforePayment)
                .newPayment(newPayment)
                // claimOne's filter guarantees the claimed candidate's first row is already a real
                // registered Cheque whenever beforeMode==CHEQUE — SAME_BANK is the correct outcome
                // for "before" by construction in that case, not independently re-resolved
                // (re-resolving would need the SAME finchqdet row ChequeBookValidator later checks
                // against, which this data source doesn't read). null for a NEFT "before" row (no
                // cheque-book concept) — this generalizes correctly to all three shapes unchanged.
                .beforeBankMatch(beforeMode == PaymentSelectionType.CHEQUE
                        ? ChequeInstrumentResolver.ChequeBankMatch.SAME_BANK : null)
                .newBankMatch(newBankMatch)
                .newBookcode(newBookcode)
                .shape(shape)
                .build();
    }

    /**
     * Resolves a genuinely future Instrument Date for the {@link PaymentInfoDateScenario}
     * regression variants — {@code header.getVchdt().plusDays(5)}, capped at the voucher's own
     * accounting year's last valid day (via {@link AccountingPeriodResolver#lastValidDay}) so this
     * never accidentally crosses into a different accounting year and trips the unrelated {@code
     * GV_EDIT_DATE_CROSSES_ACCOUNTING_YEAR} rejection instead of the instrument-date rule these
     * scenarios exist to isolate. Edge case, not specially guarded: if the voucher's own entry date
     * already IS that last valid day, the capped candidate equals (not exceeds) the entry date —
     * rare enough not to warrant extra defensive logic here.
     */
    private static LocalDate resolveFutureInstrumentDate(Jdbi jdbi, FinVchMain header) {
        AdmYear year = jdbi.onDemand(AdmYearDao.class).findByYcode(header.getYcode())
                .orElseThrow(() -> new IllegalStateException(
                        "[DbGeneralVoucherEditDataSource] admyear has no row for ycode=" + header.getYcode()));
        LocalDate candidate = header.getVchdt().plusDays(5);
        LocalDate cap = AccountingPeriodResolver.lastValidDay(year);
        return candidate.isAfter(cap) ? cap : candidate;
    }

    /**
     * Dispatches to one of 4 shape-specific builders (plan §4B) based on
     * {@link GeneralVoucherEditVariant#getDetailLineEditShape()}. {@code IN_PLACE} is the
     * original, still most common shape (amount/reversal/narration only, same GL/Sub Ledger) —
     * every variant built before the other 3 shapes existed defaults to it.
     */
    private static DetailLineEditTarget buildEditedDetailLine(Jdbi jdbi, GeneralVoucherEditVariant variant,
            List<ExpenseIncomeDetail> expenseIncomeRows, List<SiteAllocation> siteAllocations,
            List<String> gatheringLog) {
        DetailLineEditTarget.EditShape shape = variant.getDetailLineEditShape() != null
                ? variant.getDetailLineEditShape() : DetailLineEditTarget.EditShape.IN_PLACE;
        return switch (shape) {
            case IN_PLACE -> buildInPlaceEdit(variant, expenseIncomeRows, gatheringLog);
            case GL_TYPE_TRANSITION -> buildGlTypeTransition(jdbi, variant, expenseIncomeRows, gatheringLog);
            case ADD_NEW_LINE -> buildAddNewLine(jdbi, variant, expenseIncomeRows, gatheringLog);
            case SITE_SPLIT_ONLY -> buildSiteSplitOnly(jdbi, variant, expenseIncomeRows, siteAllocations, gatheringLog);
        };
    }

    /** Builds the default {@code IN_PLACE} detail-line edit shape (plan §4B shape 0): same GL/Sub
     *  Ledger as the existing row, with a new amount ({@code existing amount + AMOUNT_DELTA}),
     *  the variant's reversal toggle, and (when {@code narrationEdit} is set) a new Ledger
     *  Narration. */
    private static DetailLineEditTarget buildInPlaceEdit(GeneralVoucherEditVariant variant,
            List<ExpenseIncomeDetail> expenseIncomeRows, List<String> gatheringLog) {

        ExpenseIncomeDetail existing = variant.isRequireSharedGlcodeSibling()
                ? findRowWithSharedGlcodeSibling(expenseIncomeRows)
                : expenseIncomeRows.stream()
                        .filter(r -> r.getResolvedGlType() == variant.getGlLineType())
                        .findFirst()
                        .orElse(null);
        if (existing == null) {
            throw new SkipException(variant.isRequireSharedGlcodeSibling()
                    ? "Skipped due to lack of data: claimed voucher has no finvchdet row sharing a glcode with "
                      + "a different-slcode sibling for variant '" + variant.getVariantId() + "'."
                    : "Skipped due to lack of data: claimed voucher has no finvchdet row of GlLineType="
                      + variant.getGlLineType() + " for variant '" + variant.getVariantId() + "'.");
        }

        boolean debit = existing.isDebit();
        BigDecimal newAmount = (debit ? existing.getExpenseDr() : existing.getIncomeCr()).add(AMOUNT_DELTA);
        boolean newIsReversal = variant.isReversalToggle() != existing.isReversalYes();
        // Deliberately different from existing.getLedgerNarration() (FakerUtils generates fresh
        // random text) — mirrors newReference/newRemarks' FakerUtils usage below for the header
        // — so the snapshot-diff engine's finvchdet/finpost nartext MUST_CHANGE assertion has a
        // real change to detect.
        String newLineNarration = variant.isNarrationEdit() ? FakerUtils.randomRemarks() : existing.getLedgerNarration();

        ExpenseIncomeDetail newValues = new ExpenseIncomeDetail(
                null,
                existing.getLineNo(),
                existing.getGeneralLedger(),
                existing.getSubLedger(),
                existing.getSubLedgerId(),
                newLineNarration,
                newIsReversal ? "Yes" : "No",
                debit ? newAmount : null,
                debit ? null : newAmount);

        gatheringLog.add("  Editing existing LineNo='" + existing.getLineNo() + "' (GL='"
                + existing.getGeneralLedger() + "', Sub Ledger='" + existing.getSubLedger() + "'): new amount="
                + newAmount + ", new isReversal=" + (newIsReversal ? "Yes" : "No")
                + (variant.isNarrationEdit() ? ", new Ledger Narration='" + newLineNarration
                        + "' (was '" + existing.getLedgerNarration() + "')" : ""));

        return DetailLineEditTarget.builder()
                .lineNo(existing.getLineNo())
                .beforeRow(existing)
                .newValues(newValues)
                .shape(DetailLineEditTarget.EditShape.IN_PLACE)
                .build();
    }

    /**
     * Builds a GL-type-transition edit (plan §4B shape 1): moves an existing line to a different
     * GL of a different account class, keeping the same {@link GlLineType} (Site/Sub-Ledger
     * applicability) — V1 only ever claims/edits {@code TYPE1_NONE} lines, so no Sub-Ledger/Site
     * UI action is needed here. Excludes any glcode already used by ANOTHER line on the voucher
     * — otherwise the new row's business key {@code [vchcode, glcode, slcode]} would collide with
     * an untouched sibling line.
     */
    private static DetailLineEditTarget buildGlTypeTransition(Jdbi jdbi, GeneralVoucherEditVariant variant,
            List<ExpenseIncomeDetail> expenseIncomeRows, List<String> gatheringLog) {

        ExpenseIncomeDetail existing = expenseIncomeRows.stream()
                .filter(r -> r.getResolvedGlType() == variant.getGlLineType())
                .findFirst()
                .orElseThrow(() -> new SkipException("Skipped due to lack of data: claimed voucher has no "
                        + "finvchdet row of GlLineType=" + variant.getGlLineType() + " for variant '"
                        + variant.getVariantId() + "'."));

        GlAccountType targetClass = variant.getTargetAccountClass();
        if (targetClass == null) {
            throw new IllegalStateException("[DbGeneralVoucherEditDataSource] variant '" + variant.getVariantId()
                    + "' is GL_TYPE_TRANSITION but declares no targetAccountClass.");
        }

        FinGlDao glDao = jdbi.onDemand(FinGlDao.class);
        Set<Long> glcodesInUse = new HashSet<>();
        for (ExpenseIncomeDetail r : expenseIncomeRows) {
            FinGl g = glDao.findByGlName(r.getGeneralLedger());
            if (g != null) glcodesInUse.add(g.getGlcode());
        }

        String slapp = variant.getGlLineType().isSubLedgerApplicable() ? "Y" : "N";
        String costapp = variant.getGlLineType().isSiteApplicable() ? "Y" : "N";
        FinGl targetGl = glDao.findByTypeAndApplicability(targetClass.code(), slapp, costapp).stream()
                .filter(g -> !glcodesInUse.contains(g.getGlcode()))
                .findFirst()
                .orElseThrow(() -> new SkipException("Skipped due to lack of data: no fingl row of type="
                        + targetClass + " (" + targetClass.code() + "), slapp=" + slapp + ", costapp=" + costapp
                        + " not already used on this voucher, for variant '" + variant.getVariantId() + "'."));

        boolean debit = existing.isDebit();
        BigDecimal newAmount = (debit ? existing.getExpenseDr() : existing.getIncomeCr()).add(AMOUNT_DELTA);

        ExpenseIncomeDetail newValues = new ExpenseIncomeDetail(
                null,
                existing.getLineNo(),
                targetGl.getGlname(),
                null,   // Sub Ledger — TYPE1_NONE both sides, no Sub Ledger involved
                null,
                existing.getLedgerNarration(),
                existing.getIsReversal(),
                debit ? newAmount : null,
                debit ? null : newAmount);
        newValues.setResolvedGlType(variant.getGlLineType());
        try {
            newValues.setResolvedAccountClass(targetGl.accountType());
        } catch (IllegalArgumentException e) {
            Log.warn("[DbGeneralVoucherEditDataSource] Target GL '{}' has an unrecognized fingl.type — "
                    + "leaving resolvedAccountClass null. {}", targetGl.getGlname(), e.getMessage());
        }

        gatheringLog.add("  GL-type transition: LineNo='" + existing.getLineNo() + "' GL '"
                + existing.getGeneralLedger() + "' → '" + targetGl.getGlname() + "' (account class "
                + targetClass + "), new amount=" + newAmount);

        return DetailLineEditTarget.builder()
                .lineNo(existing.getLineNo())
                .beforeRow(existing)
                .newValues(newValues)
                .shape(DetailLineEditTarget.EditShape.GL_TYPE_TRANSITION)
                .build();
    }

    /**
     * Builds an add-new-line edit (plan §4B shape 2): appends a fresh line to the voucher.
     * Restricted to {@code TYPE1_NONE} in V1 — a Site-applicable new line would risk the
     * {@code isCostTagApplicable()} trap (that flag is resolved from the PRE-EDIT state in
     * {@code EditGeneralVoucher} Step 5, so a new line that is the document's ONLY
     * Site-applicable row would silently not get its {@code fincosttag} rows checked at all);
     * see the plan's own note on this. {@code LineNo} is one past the last existing row, which
     * doubles as the new row's 0-based grid append index once run through
     * {@code GeneralVoucherEditPredicateFactory}'s {@code resolveRowIndex}.
     */
    private static DetailLineEditTarget buildAddNewLine(Jdbi jdbi, GeneralVoucherEditVariant variant,
            List<ExpenseIncomeDetail> expenseIncomeRows, List<String> gatheringLog) {

        FinGlDao glDao = jdbi.onDemand(FinGlDao.class);
        Set<Long> glcodesInUse = new HashSet<>();
        for (ExpenseIncomeDetail r : expenseIncomeRows) {
            FinGl g = glDao.findByGlName(r.getGeneralLedger());
            if (g != null) glcodesInUse.add(g.getGlcode());
        }

        FinGl newGl = glDao.findByApplicability("N", "N").stream()
                .filter(g -> !glcodesInUse.contains(g.getGlcode()))
                .findFirst()
                .orElseThrow(() -> new SkipException("Skipped due to lack of data: no unused TYPE1_NONE "
                        + "fingl row available to add as a new line, for variant '" + variant.getVariantId() + "'."));

        String newLineNo = String.valueOf(expenseIncomeRows.size() + 1);
        boolean debit = true; // consistent, arbitrary choice — Debit side, same default TYPE1 rows use elsewhere
        BigDecimal amount = AMOUNT_DELTA;

        ExpenseIncomeDetail newRow = new ExpenseIncomeDetail(
                null, newLineNo, newGl.getGlname(), null, null,
                FakerUtils.randomRemarks(), "No", debit ? amount : null, debit ? null : amount);
        newRow.setResolvedGlType(GlLineType.TYPE1_NONE);
        try {
            newRow.setResolvedAccountClass(newGl.accountType());
        } catch (IllegalArgumentException e) {
            Log.warn("[DbGeneralVoucherEditDataSource] New line's GL '{}' has an unrecognized fingl.type — "
                    + "leaving resolvedAccountClass null. {}", newGl.getGlname(), e.getMessage());
        }

        gatheringLog.add("  Add new line: LineNo='" + newLineNo + "' GL='" + newGl.getGlname()
                + "', amount=" + amount + (debit ? " Dr" : " Cr"));

        return DetailLineEditTarget.builder()
                .lineNo(newLineNo)
                .beforeRow(null)
                .newValues(newRow)
                .shape(DetailLineEditTarget.EditShape.ADD_NEW_LINE)
                .build();
    }

    /**
     * Builds a site-split-only edit (plan §4B shape 4): same total amount/GL/narration/reversal —
     * only the sitewise allocation redistributes. Moves a fixed delta from the first already-
     * allocated site to one newly-resolved eligible site the current logged-in user is assigned
     * to, via the same resolution mechanism {@code DbGeneralVoucherDataSource}'s Create flow
     * already uses for Type2/Type3 ({@code resolveType2Sites}/{@code resolveType3Sites}).
     */
    private static DetailLineEditTarget buildSiteSplitOnly(Jdbi jdbi, GeneralVoucherEditVariant variant,
            List<ExpenseIncomeDetail> expenseIncomeRows, List<SiteAllocation> siteAllocations,
            List<String> gatheringLog) {

        ExpenseIncomeDetail existing = expenseIncomeRows.stream()
                .filter(r -> r.getResolvedGlType() == variant.getGlLineType())
                .filter(r -> siteAllocations.stream().anyMatch(a -> a.getLineNo().equals(r.getLineNo())))
                .findFirst()
                .orElseThrow(() -> new SkipException("Skipped due to lack of data: claimed voucher has no "
                        + variant.getGlLineType() + " finvchdet row with an existing site allocation, for "
                        + "variant '" + variant.getVariantId() + "'."));

        List<SiteAllocation> beforeAllocations = siteAllocations.stream()
                .filter(a -> a.getLineNo().equals(existing.getLineNo()))
                .collect(Collectors.toList());

        UserCredential threadUser = DriverManager.getCurrentUser();
        String loginName = threadUser != null ? threadUser.username() : SupplierUser.username();
        long ecode = jdbi.onDemand(HrEmpDao.class).findEcodeByAbbre(loginName)
                .orElseThrow(() -> new IllegalStateException(
                        "[DbGeneralVoucherEditDataSource] No hrdemp record found for login user: '" + loginName + "'."));
        Set<Long> siteCodeSet = new HashSet<>(jdbi.onDemand(HrEmpOuDao.class).findSiteCodesByEcode(ecode));

        FinGlDao glDao = jdbi.onDemand(FinGlDao.class);
        FinGl gl = glDao.findByGlName(existing.getGeneralLedger());
        if (gl == null) {
            throw new IllegalStateException("[DbGeneralVoucherEditDataSource] GL '" + existing.getGeneralLedger()
                    + "' (LineNo='" + existing.getLineNo() + "') not found in fingl.");
        }

        List<Long> eligibleCodes;
        String source;
        if (variant.getGlLineType() == GlLineType.TYPE3_SITE_AND_SUBLEDGER && existing.getSubLedgerId() != null) {
            FinSl sl = jdbi.onDemand(FinSlDao.class).findBySlnameAndSlid(existing.getSubLedger(), existing.getSubLedgerId());
            FinSlGl pair = sl != null ? jdbi.onDemand(FinSlGlDao.class).findByGlcodeAndSlcode(gl.getGlcode(), sl.getSlcode()) : null;
            List<FinSlGlSite> narrowSites = pair != null
                    ? jdbi.onDemand(FinSlGlSiteDao.class).findByFinslGlCode(pair.getCode())
                    : List.of();
            if (!narrowSites.isEmpty()) {
                eligibleCodes = narrowSites.stream().map(FinSlGlSite::getAdmsiteCode)
                        .filter(siteCodeSet::contains).distinct().collect(Collectors.toList());
                source = "finsl_gl_site (pair-specific override)";
            } else {
                eligibleCodes = jdbi.onDemand(FinGlSiteDao.class).findByGlcode(gl.getGlcode()).stream()
                        .map(FinGlSite::getAdmsiteCode).filter(siteCodeSet::contains).distinct().collect(Collectors.toList());
                source = "fingl_site (fallback, no finsl_gl_site override)";
            }
        } else {
            eligibleCodes = jdbi.onDemand(FinGlSiteDao.class).findByGlcode(gl.getGlcode()).stream()
                    .map(FinGlSite::getAdmsiteCode).filter(siteCodeSet::contains).distinct().collect(Collectors.toList());
            source = "fingl_site";
        }

        Set<String> alreadyAllocatedNames = beforeAllocations.stream()
                .map(SiteAllocation::getSiteName)
                .filter(java.util.Objects::nonNull)
                .map(String::trim)
                .collect(Collectors.toSet());

        AdmSiteDao admSiteDao = jdbi.onDemand(AdmSiteDao.class);
        AdmSite newSite = eligibleCodes.stream()
                .map(admSiteDao::findByCode)
                .filter(java.util.Objects::nonNull)
                .filter(s -> s.getName() != null && !alreadyAllocatedNames.contains(s.getName().trim()))
                .findFirst()
                .orElseThrow(() -> new SkipException("Skipped due to lack of data: no eligible Site (via " + source
                        + ") beyond the " + alreadyAllocatedNames.size() + " already allocated for LineNo='"
                        + existing.getLineNo() + "' — need at least one more, for variant '"
                        + variant.getVariantId() + "'."));

        SiteAllocation moveFrom = beforeAllocations.get(0);
        BigDecimal moveFromAmount = moveFrom.getAmount() != null ? moveFrom.getAmount() : BigDecimal.ZERO;
        BigDecimal delta = AMOUNT_DELTA.min(moveFromAmount);
        if (delta.signum() <= 0) {
            throw new SkipException("Skipped due to lack of data: LineNo='" + existing.getLineNo()
                    + "' first site allocation ('" + moveFrom.getSiteName() + "') has no positive amount to "
                    + "redistribute, for variant '" + variant.getVariantId() + "'.");
        }

        List<SiteAllocation> newAllocations = new ArrayList<>();
        for (SiteAllocation a : beforeAllocations) {
            SiteAllocation copy = new SiteAllocation();
            copy.setLineNo(a.getLineNo());
            copy.setSiteName(a.getSiteName());
            copy.setAmount(a == moveFrom ? moveFromAmount.subtract(delta) : a.getAmount());
            newAllocations.add(copy);
        }
        SiteAllocation added = new SiteAllocation();
        added.setLineNo(existing.getLineNo());
        added.setSiteName(newSite.getName());
        added.setAmount(delta);
        newAllocations.add(added);

        gatheringLog.add("  Site-split-only: LineNo='" + existing.getLineNo() + "' moving " + delta
                + " from '" + moveFrom.getSiteName() + "' to newly-resolved site '" + newSite.getName()
                + "' (via " + source + ") — total amount unchanged");

        // newValues is an exact copy of existing — only the site split changes below, not the
        // line's own GL/amount/narration/reversal.
        ExpenseIncomeDetail newValues = new ExpenseIncomeDetail(
                null, existing.getLineNo(), existing.getGeneralLedger(), existing.getSubLedger(),
                existing.getSubLedgerId(), existing.getLedgerNarration(), existing.getIsReversal(),
                existing.getExpenseDr(), existing.getIncomeCr());
        newValues.setResolvedGlType(existing.getResolvedGlType());
        newValues.setResolvedAccountClass(existing.getResolvedAccountClass());

        return DetailLineEditTarget.builder()
                .lineNo(existing.getLineNo())
                .beforeRow(existing)
                .newValues(newValues)
                .shape(DetailLineEditTarget.EditShape.SITE_SPLIT_ONLY)
                .beforeSiteAllocations(beforeAllocations)
                .newSiteAllocations(newAllocations)
                .build();
    }

    /** Finds the first row in {@code rows} that has ANOTHER row sharing its General Ledger with a
     *  different Sub Ledger — i.e. a genuine "two lines share a glcode with different slcode"
     *  pair, the shape {@code claimOne} guarantees exists on the candidate voucher when
     *  {@code requireSharedGlcodeSibling} is set (see that method). Deliberately does NOT filter
     *  by {@code resolvedGlType} — confirmed live that this shape only occurs on GL accounts with
     *  the unmodeled {@code slapp='Y'}/{@code costapp='N'} flag combination, for which
     *  {@code resolvedGlType} is always {@code null} (see {@code claimOne}'s Javadoc). Compares by
     *  {@code subLedgerId} (populated whenever {@code slcode != 0} via
     *  {@code FinSlDao.findBySlcode} in {@code resolveExpenseIncomeRows}) rather than
     *  {@code slcode} directly, since this method only has the already-resolved
     *  {@link ExpenseIncomeDetail} view to work with. Returns {@code null} if no such pair exists
     *  (shouldn't happen given the claim query's guarantee, but defensive rather than throwing an
     *  NPE downstream). */
    private static ExpenseIncomeDetail findRowWithSharedGlcodeSibling(List<ExpenseIncomeDetail> rows) {
        for (ExpenseIncomeDetail candidate : rows) {
            boolean hasSibling = rows.stream().anyMatch(other -> other != candidate
                    && candidate.getGeneralLedger() != null
                    && candidate.getGeneralLedger().equalsIgnoreCase(other.getGeneralLedger())
                    && !java.util.Objects.equals(candidate.getSubLedgerId(), other.getSubLedgerId()));
            if (hasSibling) return candidate;
        }
        return null;
    }

    /**
     * Claims one existing General Voucher matching {@code variant} — plan §4's selection rule:
     * <ul>
     *   <li>{@code UNPOSTED} sourceStatus — primary claim is {@code release_status='U'}; on
     *       shortfall, {@link DataAvailabilityGuard#selectOrBackfill} invokes
     *       {@link GeneralVoucherEditRevertBackfillStrategy} to revert one Posted candidate via
     *       API, then retries.</li>
     *   <li>{@code POSTED_NEEDS_REVERT} sourceStatus — claims a {@code release_status='P'}
     *       candidate directly (no automatic API backfill) so that {@code EditGeneralVoucher}'s
     *       own "Unpost if Posted" step exercises the live UI unpost action — this is plan §7
     *       scenarios 2/7's actual "exercises the unpost-in-test step" intent, distinct from the
     *       API-based backfill mechanism above (which only fires for the {@code UNPOSTED} case's
     *       shortfall, per plan §4's own wording).</li>
     * </ul>
     * For {@code DETAIL_LINE} variants, the claim query additionally joins {@code finvchdet}/
     * {@code fingl} so the claimed voucher actually has a line of the variant's required
     * {@link GlLineType} shape — and, when {@code requireSharedGlcodeSibling} is set, requires
     * that line's {@code glcode} to also appear on another {@code finvchdet} row with a
     * different {@code slcode} (see {@link #claimOne}'s sibling clause).
     *
     * <p>Every candidate {@link #claimOne} accepts is also passed through
     * {@link DocumentReservationRegistry#tryReserve} — the SQL selection above only picks a
     * vchcode that's <em>eligible</em>; the registry is what prevents a different, concurrently-
     * running test method's own {@code load()} call (this class's own {@code claimedThisRun} is
     * local to a single {@code load()} invocation and can't see across methods) from claiming the
     * same vchcode at once — confirmed live: {@code editGeneralVoucherHeader} and
     * {@code editGeneralVoucherDetailLine} run as parallel threads
     * ({@code testng-gv-edit.xml}/{@code testng-gv-edit-mandatory.xml}'s {@code thread-count="2"}),
     * and a same-second "already logged in from some other browser" collision plus an unexplained
     * no-op Save on a claimed voucher were both observed in the same live run.
     */
    private static Long claimCandidate(Jdbi jdbi, GeneralVoucherEditVariant variant, Set<Long> claimedThisRun,
            List<String> gatheringLog) {
        if (variant.getSourceStatus() == GeneralVoucherEditVariant.SourceStatus.UNPOSTED) {
            return DataAvailabilityGuard.selectOrBackfill(
                    () -> claimOne(jdbi, "U", variant, claimedThisRun, gatheringLog)
                            .orElseThrow(() -> new SkipException(
                                    "No documents found for the criteria: vchtype='G', release_status='U'"
                                    + glLineTypeSuffix(variant) + ", own accounting year/financial month "
                                    + (variant.isRequireClosedPeriod() ? "closed" : "open")
                                    + ", unclaimed this run — need 1.")),
                    1,
                    new GeneralVoucherEditRevertBackfillStrategy(variant));
        }

        return claimOne(jdbi, "P", variant, claimedThisRun, gatheringLog)
                .orElseThrow(() -> new SkipException(
                        "No documents found for the criteria: vchtype='G', release_status='P'"
                        + glLineTypeSuffix(variant) + ", own accounting year/financial month "
                        + (variant.isRequireClosedPeriod() ? "closed" : "open")
                        + ", unclaimed this run — need 1."));
    }

    private static String glLineTypeSuffix(GeneralVoucherEditVariant variant) {
        if (variant.isRequireSharedGlcodeSibling()) {
            return " with two finvchdet lines sharing a glcode but different slcode";
        }
        return variant.getGlLineType() != null ? " with a finvchdet line of GlLineType=" + variant.getGlLineType() : "";
    }

    /**
     * Scans candidates for {@code status} matching {@code variant}'s shape requirements (plain,
     * GL-line-type-joined, or shared-glcode-sibling — see the three branches below), preferring
     * the current accounting year (pass 1) before falling back to any open-period candidate
     * (pass 2) — mirrors {@link DbGeneralVoucherRevertDataSource#claimOnePostedVoucher}'s
     * two-pass shape. {@code requireClosedPeriod} variants invert both the scan order (oldest
     * first) and the period filter (want CLOSED, not open).
     */
    private static Optional<Long> claimOne(Jdbi jdbi, String status, GeneralVoucherEditVariant variant,
            Set<Long> claimedThisRun, List<String> gatheringLog) {
        List<FinVchMain> candidates = fetchCandidates(jdbi, status, variant);
        return claimFromCandidates(jdbi, variant, candidates, claimedThisRun, gatheringLog);
    }

    /**
     * The shape-filtered, ordered candidate list for {@code variant} at {@code status} — the SQL half
     * of {@link #claimOne}, public so {@code GeneralVoucherEditRevertBackfillStrategy} applies the
     * IDENTICAL shape filters (first-row Cheque/NEFT mode + chqno, Cash/Bank ledger type, shared-glcode
     * sibling, GL-line-type join) when choosing which Posted voucher to revert: a voucher this query
     * would reject once Unposted is useless to revert. Reservation and period filtering are NOT done
     * here — see {@link #acceptanceFilter}.
     */
    public static List<FinVchMain> fetchCandidates(Jdbi jdbi, String status, GeneralVoucherEditVariant variant) {

        // ! requireClosedPeriod variants deliberately want an OLD, closed-period document — scanning
        // newest-first (the default, correct for ordinary open-period variants) forces the scan
        // through every currently-open candidate first. Confirmed live (Jenkins run
        // 20260831_213639 / #13.txt): DbGeneralVoucherDeleteDataSource's identical shared-order
        // bug took ~23 minutes / 2000+ rejected candidates to find one closed-period document.
        // Oldest-first for requireClosedPeriod, mirroring
        // DbGeneralVoucherRevertDataSource#claimOneClosedPeriodVoucher (round 5 fix).
        String orderBy = variant.isRequireClosedPeriod()
                ? "ORDER BY m.ycode ASC, m.vchdt ASC"
                : "ORDER BY m.ycode DESC, m.vchdt DESC";

        // Finding A (Round 12): each branch below now SELECTs m.* (not just m.vchcode) and bulk-maps
        // to FinVchMain via .mapToBean — scanAndClaim's own scan loop used to re-fetch each
        // candidate's full row individually (SELECT * FROM finvchmain WHERE vchcode = ? per
        // candidate), the dominant cost of a deep/exhausted scan. Filters/order/reservation
        // bookkeeping are all unchanged.
        List<FinVchMain> candidates;
        if (variant.getScenarioType() == GeneralVoucherEditVariant.ScenarioType.DETAIL_LINE && variant.isRequireSharedGlcodeSibling()) {
            // No fingl join / slapp+costapp filter here, unlike the glLineType branch below —
            // confirmed live in idealdb that the "two finvchdet rows share a glcode with
            // different slcode" shape only occurs on GL accounts with slapp='Y'/costapp='N',
            // the unmodeled 4th GlLineType flag combination (GlLineType.fromFlags() throws for
            // it, so resolvedGlType stays null for these rows — see resolveExpenseIncomeRows),
            // not any of TYPE1_NONE/TYPE2_SITE_ONLY/TYPE3_SITE_AND_SUBLEDGER. Guarantees
            // buildEditedDetailLine's findRowWithSharedGlcodeSibling always finds a match rather
            // than skipping the scenario for lack of data.
            //
            // No DISTINCT (round 6 fix) — the "shares a glcode with a different slcode" match is
            // expressed entirely inside an EXISTS subquery (two finvchdet aliases, d and d3),
            // never a JOIN against the outer query, so m.vchcode stays unique per row. The
            // previous SELECT DISTINCT m.vchcode ... JOIN finvchdet d ... ORDER BY m.ycode,
            // m.vchdt shape was a hard PostgreSQL error (every ORDER BY expression must appear in
            // the SELECT DISTINCT list) — confirmed live to have crashed this exact shape in
            // DbGeneralVoucherRevertDataSource#claimOnePostedVoucher; this branch carried the
            // identical latent bug, just not yet triggered by a MANDATORY-group run.
            String sql =
                    "SELECT m.* FROM finvchmain m " +
                    "WHERE m.vchtype = 'G' AND m.release_status = :status " +
                    "AND NOT EXISTS (SELECT 1 FROM finvchdet d2 WHERE d2.vchcode = m.vchcode AND d2.brsdt IS NOT NULL) " +
                    "AND EXISTS (SELECT 1 FROM finvchdet d, finvchdet d3 WHERE d.vchcode = m.vchcode " +
                    "AND d3.vchcode = m.vchcode AND d3.glcode = d.glcode AND d3.slcode <> d.slcode) " +
                    orderBy;
            candidates = jdbi.withHandle(h -> h.createQuery(sql).bind("status", status).mapToBean(FinVchMain.class).list());
        } else if (variant.getScenarioType() == GeneralVoucherEditVariant.ScenarioType.DETAIL_LINE && variant.getGlLineType() != null) {
            String slapp = variant.getGlLineType().isSubLedgerApplicable() ? "Y" : "N";
            String costapp = variant.getGlLineType().isSiteApplicable() ? "Y" : "N";
            // No DISTINCT (round 6 fix) — same EXISTS rewrite as above. Confirmed live this exact
            // shape (SELECT DISTINCT ... JOIN finvchdet ... JOIN fingl ... ORDER BY m.ycode,
            // m.vchdt) crashed DbGeneralVoucherRevertDataSource#claimOnePostedVoucher with
            // PSQLException "for SELECT DISTINCT, ORDER BY expressions must appear in select
            // list" — this branch carries the identical latent bug.
            String sql =
                    "SELECT m.* FROM finvchmain m " +
                    "WHERE m.vchtype = 'G' AND m.release_status = :status " +
                    "AND EXISTS (SELECT 1 FROM finvchdet d JOIN fingl g ON g.glcode = d.glcode " +
                    "WHERE d.vchcode = m.vchcode AND g.slapp = :slapp AND g.costapp = :costapp) " +
                    "AND NOT EXISTS (SELECT 1 FROM finvchdet d2 WHERE d2.vchcode = m.vchcode AND d2.brsdt IS NOT NULL) " +
                    orderBy;
            candidates = jdbi.withHandle(h -> h.createQuery(sql)
                    .bind("status", status).bind("slapp", slapp).bind("costapp", costapp)
                    .mapToBean(FinVchMain.class).list());
        } else {
            // Optional Cash/Bank-ledger-type constraint (Round 22 GV plan, FIN-GV-EDIT-HEADER-04):
            // only the two FUTURE_DATE variants set requiredCashBankType today, so this join/
            // filter is a no-op for every other header-only variant — same candidate set as
            // before for them.
            CashBankType requiredCashBankType = variant.getRequiredCashBankType();
            String cashBankJoin = requiredCashBankType != null
                    ? "JOIN fingl gcb ON gcb.glcode = m.glcode " : "";
            String cashBankFilter = requiredCashBankType != null
                    ? "AND gcb.srctype = :cashBankSrctype " : "";
            // PAYMENT_INFO-only: the claimed candidate's FIRST finvchdet row (ORDER BY code —
            // FinVchDetDao#findByVoucherCode's own "row 1 is the single Cash/Bank counter-leg"
            // convention, see PaymentInformation's Javadoc) must already be in the SOURCE mode for
            // this scenario's shape, with a real instrument no. — there has to be an existing
            // value to change. SAME_MODE_NEW_INSTRUMENT/CHEQUE_TO_NEFT both start from Cheque
            // ('C'); NEFT_TO_CHEQUE starts from NEFT/RTGS ('R').
            String firstRowChequeFilter = "";
            if (variant.getScenarioType() == GeneralVoucherEditVariant.ScenarioType.PAYMENT_INFO) {
                String requiredSourceMode =
                        variant.getPaymentInfoEditShape() == PaymentInfoEditTarget.PaymentModeShape.NEFT_TO_CHEQUE
                                ? "R" : "C";
                // PaymentInfoDateScenario-only: pin the counter-leg to Dr or Cr — see that enum's
                // own class Javadoc for why (GvPaymentInformationPopup's known/unknown-cheque
                // branching on a Dr row skips setInstrumentDate entirely for a synthetic instrument
                // number, so a future-date override would never actually reach the UI unless the
                // row is genuinely the required side). No-op (both left blank) for every ordinary
                // PAYMENT_INFO variant, matching today's existing "whichever row comes first" behavior.
                PaymentInfoDateScenario dateScenario = variant.getDateScenario();
                String drCrFilter = dateScenario == null ? ""
                        : dateScenario.requireCrRow()
                                ? "AND d1.camount IS NOT NULL AND d1.camount <> 0 "
                                : "AND d1.damount IS NOT NULL AND d1.damount <> 0 ";
                firstRowChequeFilter = "AND EXISTS (SELECT 1 FROM (SELECT * FROM finvchdet WHERE vchcode = m.vchcode "
                        + "ORDER BY code LIMIT 1) d1 WHERE d1.payment_mode = '" + requiredSourceMode
                        + "' AND d1.chqno IS NOT NULL " + drCrFilter + ") ";
            }
            String sql =
                    "SELECT m.* FROM finvchmain m " + cashBankJoin +
                    "WHERE m.vchtype = 'G' AND m.release_status = :status " + cashBankFilter + firstRowChequeFilter +
                    "AND NOT EXISTS (SELECT 1 FROM finvchdet d2 WHERE d2.vchcode = m.vchcode AND d2.brsdt IS NOT NULL) " +
                    orderBy;
            candidates = jdbi.withHandle(h -> {
                var query = h.createQuery(sql).bind("status", status);
                if (requiredCashBankType != null) {
                    query = query.bind("cashBankSrctype", requiredCashBankType.srcType());
                }
                return query.mapToBean(FinVchMain.class).list();
            });
        }
        return candidates;
    }

    /** The period / future-date-room acceptance test {@link #claimOne} applies to every candidate —
     *  public for the same reason as {@link #fetchCandidates}. */
    public static java.util.function.Predicate<FinVchMain> acceptanceFilter(Jdbi jdbi,
            GeneralVoucherEditVariant variant, AccountingPeriodCache periodCache) {
        boolean requiresFutureDateRoom = variant.getDateVariant() == VoucherEditDateVariant.FUTURE_DATE;
        return header -> {
            boolean periodOpen = periodCache.isEditable(jdbi, header, PeriodDomain.FINANCE);
            if (variant.isRequireClosedPeriod() ? !periodOpen : periodOpen) {
                // continues below
            } else {
                return false;
            }
            if (requiresFutureDateRoom) {
                AdmYear year = jdbi.onDemand(AdmYearDao.class).findByYcode(header.getYcode()).orElse(null);
                return year != null && AccountingPeriodResolver.supportsFutureDateVariant(year);
            }
            return true;
        };
    }

    private static Optional<Long> claimFromCandidates(Jdbi jdbi, GeneralVoucherEditVariant variant,
            List<FinVchMain> candidates, Set<Long> claimedThisRun, List<String> gatheringLog) {
        // ORDER BY above uses ycode/vchdt, never vchcode — an insert-order surrogate key with no
        // guaranteed relationship to accounting period or document date (a backdated/corrective
        // entry can carry a high vchcode but an old ycode/vchdt); see
        // AccountingPeriodResolver#findCurrentYear's Javadoc. Direction is ASC for
        // requireClosedPeriod variants, DESC otherwise (round 5 fix) — see orderBy above.

        // Memoizes the OU/year/month lookups the filter below would otherwise re-query per
        // rejected candidate across both scan passes — see AccountingPeriodCache's own Javadoc.
        // Created BEFORE logAndResolveCurrentYcode (Finding C, Round 12 continued) so that call's
        // own first-candidate period lookup is cached too, not re-queried moments later by
        // the filter's own isEditable check for that same candidate.
        AccountingPeriodCache periodCache = new AccountingPeriodCache();

        // requireClosedPeriod variants deliberately want a NON-current (closed) year — current-
        // year preference only applies to ordinary, open-period-seeking variants.
        Long currentYcode = variant.isRequireClosedPeriod()
                ? null
                : AccountingPeriodResolver.logAndResolveCurrentYcode(jdbi, candidates, gatheringLog, periodCache);

        java.util.function.Predicate<FinVchMain> baseFilter = acceptanceFilter(jdbi, variant, periodCache);

        // Pass 1: current-year candidates only (skipped entirely for requireClosedPeriod variants
        // — currentYcode is null for those, so this pass is a guaranteed no-op).
        if (currentYcode != null) {
            Optional<Long> claimed = scanAndClaim(candidates, claimedThisRun,
                    baseFilter.and(header -> header.getYcode() == currentYcode));
            if (claimed.isPresent()) return claimed;
        }
        // Pass 2 (fallback): any candidate matching baseFilter, same order as fetched above.
        return scanAndClaim(candidates, claimedThisRun, baseFilter);
    }

    /**
     * Shared claim scan (mirrors {@code DbGeneralVoucherRevertDataSource#scanForClaim}): walks
     * {@code candidates} in their already-fetched order, accepting the first one not already
     * claimed this run whose header satisfies {@code filter} — reserving it via
     * {@link DocumentReservationRegistry} before returning.
     *
     * <p>{@code claimedThisRun} above only protects this one {@code load()} call —
     * {@code editGeneralVoucherHeader} and {@code editGeneralVoucherDetailLine} each trigger
     * their own separate {@code load()} call (and {@code testng-gv-edit.xml}/
     * {@code testng-gv-edit-mandatory.xml} run them with {@code thread-count="2"}), so nothing
     * stops both from independently claiming and editing the SAME vchcode at once.
     * {@link DocumentReservationRegistry} — the same JVM-wide, cross-thread reservation registry
     * AR/AP's document-adjustment flows already use (domain-agnostic by design: keyed on a plain
     * String, released via {@code WebTest}'s own {@code @AfterMethod(alwaysRun=true)} teardown,
     * which {@code EditGeneralVoucher} already inherits) — is what actually prevents that: if
     * another thread/test method already reserved this vchcode, try the next candidate instead.
     */
    private static Optional<Long> scanAndClaim(List<FinVchMain> candidates,
            Set<Long> claimedThisRun, java.util.function.Predicate<FinVchMain> filter) {
        for (FinVchMain header : candidates) {
            long vchcode = header.getVchcode();
            if (claimedThisRun.contains(vchcode)) continue;
            if (!filter.test(header)) continue;

            if (!DocumentReservationRegistry.tryReserve(String.valueOf(vchcode))) {
                continue;
            }

            claimedThisRun.add(vchcode);
            return Optional.of(vchcode);
        }
        return Optional.empty();
    }
}
