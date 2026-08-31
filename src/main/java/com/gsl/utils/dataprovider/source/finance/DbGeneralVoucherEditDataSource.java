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
import com.gsl.enums.Finance.Operations.Common.DocumentMessageBoxMessage;
import com.gsl.enums.Finance.Operations.GeneralVoucher.GlLineType;
import com.gsl.enums.GlAccountType;
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
import com.gsl.testdata.Finance.Operations.Vouchers.GeneralVoucher.SiteAllocation;
import com.gsl.testdata.UserCredential;
import com.gsl.testdata.dto.GeneralVoucherOperationMode;
import com.gsl.testdata.dto.MergedFinanceGeneralVoucherData;
import com.gsl.utils.Faker.FakerUtils;
import com.gsl.utils.Log;
import com.gsl.utils.dataprovider.fixture.GeneralVoucherEditFixtureFactory;
import com.gsl.utils.dataprovider.fixture.finance.GeneralVoucherEditCombinationFilter;
import com.gsl.utils.dataprovider.fixture.finance.GeneralVoucherEditCombinations;
import com.gsl.utils.dataprovider.fixture.finance.GeneralVoucherEditExecutionGroup;
import com.gsl.utils.dataprovider.fixture.finance.GeneralVoucherEditVariant;
import com.gsl.utils.dataprovider.fixture.finance.NegativeValidation;
import com.gsl.utils.dataprovider.fixture.finance.VoucherEditDateVariant;
import com.gsl.utils.dataprovider.source.TestDataSource;
import com.gsl.utils.dataprovider.source.finance.common.AccountingPeriodResolver;
import org.jdbi.v3.core.Jdbi;
import org.testng.SkipException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * DB-driven test-data source for {@code editGeneralVoucherHeader}/{@code editGeneralVoucherDetailLine}
 * — sweeps the fixed 13-scenario {@link GeneralVoucherEditCombinations#allCombinations()} matrix,
 * filtered by the {@code gv.edit.group} parameter (kept deliberately distinct from Create's
 * {@code gv.group} — see {@link GeneralVoucherEditExecutionGroup}'s Javadoc), and for each
 * surviving variant matching the requested test method's scenario type (HEADER_ONLY vs
 * DETAIL_LINE — a variant whose {@link GeneralVoucherEditVariant#getScenarioType()} doesn't match
 * the running method is skipped, not emitted, since the two test methods share this one matrix):
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

    public DbGeneralVoucherEditDataSource() {
        this(null);
    }

    public DbGeneralVoucherEditDataSource(String xmlEditGroupParam) {
        this.xmlEditGroupParam = xmlEditGroupParam;
    }

    @Override
    public List<MergedFinanceGeneralVoucherData> load(String methodName) {
        GeneralVoucherEditExecutionGroup activeGroup = GeneralVoucherEditCombinationFilter.resolveActiveGroup(xmlEditGroupParam);

        if (!"editGeneralVoucherHeader".equals(methodName) && !"editGeneralVoucherDetailLine".equals(methodName)) {
            throw new SkipException("[DbGeneralVoucherEditDataSource] Unknown method '" + methodName
                    + "' — only editGeneralVoucherHeader/editGeneralVoucherDetailLine are supported.");
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
            vchcode = claimCandidate(jdbi, variant, claimedThisRun);
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

            return MergedFinanceGeneralVoucherData.builder()
                    .scenarioTypeLabel(variant.getVariantId())
                    .existingVoucherNo(header.getSchemeDocno())
                    .releaseStatus(header.getReleaseStatus())
                    .currentDocDate(header.getVchdt())
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

        // Scope fix: a DETAIL_LINE scenario must edit ONLY the detail line — the header's
        // date/reference/remarks stay exactly as they were before this Save. Generating a new
        // date/reference/remarks unconditionally here (the former behavior) silently turned every
        // "detail-only" scenario into a combined header+detail edit, which is exactly the
        // scope-ambiguity the report review flagged. That combined-edit shape is real and will be
        // covered later by its own regression-phase scenarios — not folded invisibly into this one.
        boolean isDetailLineScenario = variant.getScenarioType() == GeneralVoucherEditVariant.ScenarioType.DETAIL_LINE;

        LocalDate newDate;
        String newReference;
        String newRemarks;
        NegativeValidation expectedValidation;

        if (isDetailLineScenario) {
            newDate = header.getVchdt();
            newReference = header.getRefno();
            newRemarks = header.getNartext();
            expectedValidation = null;
            gatheringLog.add("  DETAIL_LINE scenario — header date/reference/remarks left unchanged "
                    + "(date=" + newDate + ", refno='" + newReference + "')");
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

            // Non-null only for CROSS_YEAR_INVALID — the app is expected to REJECT the save with
            // this exact message rather than succeed (see EditGeneralVoucher's Step 9b). FUTURE_DATE
            // is NOT a rejection case — confirmed live the app accepts a future-dated document
            // within the same accounting year, so it falls through to `default -> null` and flows
            // through Step 9b's normal positive save + full snapshot-validation path. Only reachable
            // when changeDate is true — a scope that holds the date unchanged can never be rejected
            // on accounting-year grounds.
            expectedValidation = changeDate ? switch (dateVariant) {
                case CROSS_YEAR_INVALID -> NegativeValidation.GV_EDIT_DATE_CROSSES_ACCOUNTING_YEAR;
                default -> null;
            } : null;

            gatheringLog.add("  Header field scope=" + scope + " — date="
                    + (changeDate ? newDate + " (dateVariant=" + dateVariant
                        + (expectedValidation != null ? ", expecting REJECTION: " + expectedValidation.expectedMessage()
                           : dateVariant == VoucherEditDateVariant.FUTURE_DATE
                                ? ", intentionally future-dated — app is confirmed to ACCEPT this, full "
                                  + "snapshot validation applies same as any other positive header edit"
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

    private static List<ExpenseIncomeDetail> resolveExpenseIncomeRows(
            List<FinVchDet> detailRows, FinGlDao glDao, FinSlDao slDao) {
        List<ExpenseIncomeDetail> rows = new ArrayList<>();
        for (int i = 0; i < detailRows.size(); i++) {
            FinVchDet d = detailRows.get(i);
            FinGl gl = glDao.findByGlCode(d.getGlcode());
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
                FinSl sl = slDao.findBySlcode(d.getSlcode());
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
        String loginName = threadUser != null ? threadUser.username() : ConfigFactory.getConfig().username();
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
    private static Long claimCandidate(Jdbi jdbi, GeneralVoucherEditVariant variant, Set<Long> claimedThisRun) {
        if (variant.getSourceStatus() == GeneralVoucherEditVariant.SourceStatus.UNPOSTED) {
            return DataAvailabilityGuard.selectOrBackfill(
                    () -> claimOne(jdbi, "U", variant, claimedThisRun)
                            .orElseThrow(() -> new SkipException(
                                    "Skipped due to lack of data: no unclaimed Unposted (release_status='U') "
                                    + "General Voucher found" + glLineTypeSuffix(variant) + " — need 1.")),
                    1,
                    new GeneralVoucherEditRevertBackfillStrategy());
        }

        return claimOne(jdbi, "P", variant, claimedThisRun)
                .orElseThrow(() -> new SkipException(
                        "Skipped due to lack of data: no unclaimed Posted (release_status='P') General Voucher "
                        + "found" + glLineTypeSuffix(variant) + " — need 1."));
    }

    private static String glLineTypeSuffix(GeneralVoucherEditVariant variant) {
        if (variant.isRequireSharedGlcodeSibling()) {
            return " with two finvchdet lines sharing a glcode but different slcode";
        }
        return variant.getGlLineType() != null ? " with a finvchdet line of GlLineType=" + variant.getGlLineType() : "";
    }

    private static Optional<Long> claimOne(Jdbi jdbi, String status, GeneralVoucherEditVariant variant,
            Set<Long> claimedThisRun) {

        List<Long> candidates;
        if (variant.getScenarioType() == GeneralVoucherEditVariant.ScenarioType.DETAIL_LINE && variant.isRequireSharedGlcodeSibling()) {
            // No fingl join / slapp+costapp filter here, unlike the glLineType branch below —
            // confirmed live in idealdb that the "two finvchdet rows share a glcode with
            // different slcode" shape only occurs on GL accounts with slapp='Y'/costapp='N',
            // the unmodeled 4th GlLineType flag combination (GlLineType.fromFlags() throws for
            // it, so resolvedGlType stays null for these rows — see resolveExpenseIncomeRows),
            // not any of TYPE1_NONE/TYPE2_SITE_ONLY/TYPE3_SITE_AND_SUBLEDGER. Guarantees
            // buildEditedDetailLine's findRowWithSharedGlcodeSibling always finds a match rather
            // than skipping the scenario for lack of data.
            String sql =
                    "SELECT DISTINCT m.vchcode FROM finvchmain m " +
                    "JOIN finvchdet d ON d.vchcode = m.vchcode " +
                    "WHERE m.vchtype = 'G' AND m.release_status = :status " +
                    "AND NOT EXISTS (SELECT 1 FROM finvchdet d2 WHERE d2.vchcode = m.vchcode AND d2.brsdt IS NOT NULL) " +
                    "AND EXISTS (SELECT 1 FROM finvchdet d3 WHERE d3.vchcode = m.vchcode " +
                    "AND d3.glcode = d.glcode AND d3.slcode <> d.slcode) " +
                    "ORDER BY m.vchcode";
            candidates = jdbi.withHandle(h -> h.createQuery(sql).bind("status", status).mapTo(Long.class).list());
        } else if (variant.getScenarioType() == GeneralVoucherEditVariant.ScenarioType.DETAIL_LINE && variant.getGlLineType() != null) {
            String slapp = variant.getGlLineType().isSubLedgerApplicable() ? "Y" : "N";
            String costapp = variant.getGlLineType().isSiteApplicable() ? "Y" : "N";
            String sql =
                    "SELECT DISTINCT m.vchcode FROM finvchmain m " +
                    "JOIN finvchdet d ON d.vchcode = m.vchcode " +
                    "JOIN fingl g ON g.glcode = d.glcode " +
                    "WHERE m.vchtype = 'G' AND m.release_status = :status " +
                    "AND g.slapp = :slapp AND g.costapp = :costapp " +
                    "AND NOT EXISTS (SELECT 1 FROM finvchdet d2 WHERE d2.vchcode = m.vchcode AND d2.brsdt IS NOT NULL) " +
                    "ORDER BY m.vchcode";
            candidates = jdbi.withHandle(h -> h.createQuery(sql)
                    .bind("status", status).bind("slapp", slapp).bind("costapp", costapp)
                    .mapTo(Long.class).list());
        } else {
            String sql =
                    "SELECT m.vchcode FROM finvchmain m " +
                    "WHERE m.vchtype = 'G' AND m.release_status = :status " +
                    "AND NOT EXISTS (SELECT 1 FROM finvchdet d2 WHERE d2.vchcode = m.vchcode AND d2.brsdt IS NOT NULL) " +
                    "ORDER BY m.vchcode";
            candidates = jdbi.withHandle(h -> h.createQuery(sql).bind("status", status).mapTo(Long.class).list());
        }

        // Candidate eligibility guard (plan "GV Edit: accounting-year-aware dates..."): a document
        // sitting in a closed accounting year or closed financial month cannot be edited at all in
        // the live app — skip such candidates rather than claiming one a positive test could never
        // actually save against. Requires a per-candidate header fetch since the claim query above
        // doesn't (and can't cheaply) filter on admou_year/admou_month itself.
        FinVchMainDao headerDao = jdbi.onDemand(FinVchMainDao.class);
        boolean requiresFutureDateRoom = variant.getDateVariant() == VoucherEditDateVariant.FUTURE_DATE;

        for (Long vchcode : candidates) {
            if (claimedThisRun.contains(vchcode)) continue;

            FinVchMain header = headerDao.findByVoucherCode(vchcode).orElse(null);
            if (header == null) continue;
            boolean periodOpen = AccountingPeriodResolver.isEditable(jdbi, header, PeriodDomain.FINANCE);
            // Every ordinary variant needs an OPEN period; the one requireClosedPeriod negative
            // variant (GV_EDIT_HEADER_POSTED_MONTH_CLOSED_NEGATIVE) needs the exact inverse — a
            // CLOSED period — so it can exercise EditGeneralVoucher's "Unpost if Posted" prep step
            // hitting the app's own closed-period rejection.
            if (variant.isRequireClosedPeriod() ? periodOpen : !periodOpen) continue;

            // FUTURE_DATE additionally needs a fiscal year still "in progress" (room after today)
            // — a past/fully-elapsed year would make "date > today" indistinguishable from simply
            // crossing into a different (later) year.
            if (requiresFutureDateRoom) {
                AdmYear year = jdbi.onDemand(AdmYearDao.class).findByYcode(header.getYcode()).orElse(null);
                if (year == null || !AccountingPeriodResolver.supportsFutureDateVariant(year)) continue;
            }

            // claimedThisRun above only protects this one load() call — editGeneralVoucherHeader
            // and editGeneralVoucherDetailLine each trigger their own separate load() call (and
            // testng-gv-edit.xml/testng-gv-edit-mandatory.xml run them with thread-count="2"), so
            // nothing stops both from independently claiming and editing the SAME vchcode at once.
            // DocumentReservationRegistry — the same JVM-wide, cross-thread reservation registry
            // AR/AP's document-adjustment flows already use (com.gsl.database, domain-agnostic by
            // design: keyed on a plain String, released via WebTest's own
            // @AfterMethod(alwaysRun=true) tearDown, which EditGeneralVoucher already inherits) —
            // is what actually prevents that: if another thread/test method already reserved this
            // vchcode, try the next candidate instead.
            if (!DocumentReservationRegistry.tryReserve(String.valueOf(vchcode))) {
                continue;
            }

            claimedThisRun.add(vchcode);
            return Optional.of(vchcode);
        }
        return Optional.empty();
    }
}
