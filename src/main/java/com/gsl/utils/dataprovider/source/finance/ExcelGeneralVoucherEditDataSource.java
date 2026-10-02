package com.gsl.utils.dataprovider.source.finance;

import com.gsl.config.ConfigFactory;
import com.gsl.database.DatabaseManager;
import com.gsl.database.dao.finance.FinVchMainDao;
import com.gsl.enums.PeriodDomain;
import com.gsl.model.finance.FinVchMain;
import com.gsl.testdata.Finance.Operations.Vouchers.GeneralVoucher.DetailLineEditTarget;
import com.gsl.testdata.Finance.Operations.Vouchers.GeneralVoucher.DocumentInformation;
import com.gsl.testdata.Finance.Operations.Vouchers.GeneralVoucher.EditVoucherInfo;
import com.gsl.testdata.Finance.Operations.Vouchers.GeneralVoucher.ExpenseIncomeDetail;
import com.gsl.testdata.Finance.Operations.Vouchers.GeneralVoucher.SiteAllocation;
import com.gsl.testdata.MasterTestData;
import com.gsl.testdata.dto.GeneralVoucherOperationMode;
import com.gsl.testdata.dto.MergedFinanceGeneralVoucherData;
import com.gsl.utils.Log;
import com.gsl.utils.dataprovider.fixture.finance.VoucherEditDateVariant;
import com.gsl.utils.dataprovider.fixture.finance.VoucherEditEntryMethod;
import com.gsl.utils.dataprovider.source.TestDataSource;
import com.gsl.utils.dataprovider.source.finance.common.AccountingPeriodResolver;
import io.github.sskorol.data.XlsxReader;
import org.jdbi.v3.core.Jdbi;
import org.testng.SkipException;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static io.github.sskorol.data.TestDataReader.use;

/**
 * Excel-driven test-data source for {@code EditGeneralVoucher}'s two scenarios
 * ({@code editGeneralVoucherHeader}/{@code editGeneralVoucherDetailLine}) — plan §5.
 *
 * <h3>Data flow</h3>
 * <ol>
 *   <li>Read {@code MasterTestData} (shared sheet), filtered by {@code testcasename == methodName}.</li>
 *   <li>Read the new {@link EditVoucherInfo} sheet, joined to the filtered {@code MasterTestData}
 *       rows by {@code TestCaseID}.</li>
 *   <li>Read the already-existing {@code ExpenseIncomeDetail}/{@code SiteAllocation} sheets,
 *       filtered by the same {@code TestCaseID}, as the declared "before" state to diff against —
 *       the tester is asserting the named voucher's DB rows already match those sheet rows before
 *       the edit runs (mirrors {@code RevertGeneralVoucherDataSource}'s "existing voucher" idiom,
 *       extended with these two sheets since Edit additionally needs a before-state description).</li>
 *   <li>Build a new {@link DocumentInformation} with {@code voucherno} left {@code null} (so
 *       {@code VoucherDocumentInformationBase#setDocScheme}'s blank-guard skips the field
 *       entirely, never touching/reading it — passing the existing value instead was tried
 *       first but timed out waiting for the field's visibility on a live run; see that
 *       method's Javadoc) and the new date/reference/remarks. {@code New Date} blank defaults
 *       to an accounting-year-aware date; a future date is passed through as-is (confirmed live
 *       the app accepts a future-dated document within the same accounting year — no longer
 *       skipped).</li>
 *   <li>For {@code Edit Scenario=DETAIL_LINE} rows, resolves the existing {@code Edited Line No.}
 *       row and builds a {@link DetailLineEditTarget} with the new amount/reversal (same GL/Sub
 *       Ledger as the existing row — those never change) and, when {@code New Ledger Narration}
 *       is non-blank, a new per-line narration too (blank/absent leaves it unchanged, the
 *       original V1 behavior).</li>
 *   <li>Sets {@code operationMode=EDIT}, {@code existingVoucherNo}. No "Release Status" is set
 *       here (plan §16 — the column was removed entirely) — {@code EditGeneralVoucher}'s own "DB
 *       Pre-Check" step reads {@code finvchmain.release_status} live instead, since a declarative
 *       sheet value would go stale on its own once the Release-after-save dialog (§14) fires Yes
 *       against this same repeatedly-run voucher.</li>
 * </ol>
 *
 * <p><b>IMPORTANT — manual step required before running:</b> the sample rows shipped in
 * {@code GeneralVoucher.xlsx}'s {@code EditVoucherInfo} tab use PLACEHOLDER "Existing Voucher No."
 * values (e.g. {@code "VG/00120/25-26"}) copied from the plan — they will NOT exist in a real
 * database. Replace them with a real, currently-existing General Voucher number from your test
 * environment before running {@code EditGeneralVoucher} against {@code testdata.source=EXCEL}.
 */
public class ExcelGeneralVoucherEditDataSource implements TestDataSource<MergedFinanceGeneralVoucherData> {

    /**
     * Reads {@code MasterTestData}, {@code EditVoucherInfo}, {@code ExpenseIncomeDetail}, and
     * {@code SiteAllocation}, filters to rows matching {@code methodName} with a corresponding
     * {@code EditVoucherInfo} row, then delegates each surviving row to {@link #buildOne} — see
     * the class Javadoc's "Data flow" for what each built row contains.
     */
    @Override
    public List<MergedFinanceGeneralVoucherData> load(String methodName) {

        List<MasterTestData> masterData = use(XlsxReader.class)
                .withTarget(MasterTestData.class)
                .withSource(ConfigFactory.getExcelDataConfig().MasterTestData())
                .read().toList();

        List<EditVoucherInfo> editInfoList = use(XlsxReader.class)
                .withTarget(EditVoucherInfo.class)
                .withSource(ConfigFactory.getExcelDataConfig().generalVoucherPath())
                .read().toList();

        List<ExpenseIncomeDetail> allDetailRows = use(XlsxReader.class)
                .withTarget(ExpenseIncomeDetail.class)
                .withSource(ConfigFactory.getExcelDataConfig().generalVoucherPath())
                .read().toList();

        List<SiteAllocation> allSiteRows = use(XlsxReader.class)
                .withTarget(SiteAllocation.class)
                .withSource(ConfigFactory.getExcelDataConfig().generalVoucherPath())
                .read().toList();

        List<MasterTestData> nameMatched = masterData.stream()
                .filter(tc -> methodName.equalsIgnoreCase(tc.getTestcasename()))
                .collect(Collectors.toList());

        Log.info("[ExcelGeneralVoucherEditDataSource] MasterTestData total rows read : {}", masterData.size());
        Log.info("[ExcelGeneralVoucherEditDataSource] Rows matching method '{}' : {}", methodName, nameMatched.size());

        if (nameMatched.isEmpty()) {
            throw new SkipException(
                    "[ExcelGeneralVoucherEditDataSource] No MasterTestData row found for method='"
                    + methodName + "'. Add a row with testcasename='" + methodName + "' to the "
                    + "MasterTestData sheet.");
        }

        List<MasterTestData> filteredCases = nameMatched.stream()
                .filter(tc -> {
                    boolean found = editInfoList.stream()
                            .anyMatch(r -> r.getTestCaseId().equalsIgnoreCase(tc.getTestcaseid()));
                    if (!found) {
                        Log.warn("  [SKIPPED] TestCaseID='{}' has no row in EditVoucherInfo sheet",
                                tc.getTestcaseid());
                    }
                    return found;
                })
                .collect(Collectors.toList());

        if (filteredCases.isEmpty()) {
            throw new SkipException(
                    "[ExcelGeneralVoucherEditDataSource] No EditVoucherInfo row matches any "
                    + "MasterTestData TestCaseID for method='" + methodName + "'. Ensure the "
                    + "'EditVoucherInfo' sheet has a row whose TestCaseID matches.");
        }

        int total = filteredCases.size();
        AtomicInteger counter = new AtomicInteger(0);

        return filteredCases.stream()
                .map(tc -> buildOne(tc, editInfoList, allDetailRows, allSiteRows, counter.incrementAndGet(), total))
                .collect(Collectors.toList());
    }

    /**
     * Builds one {@link MergedFinanceGeneralVoucherData} for {@code tc}: fetches the claimed
     * voucher's live {@code finvchmain} header, computes the new date/reference/remarks (held at
     * the header's existing values for a {@code DETAIL_LINE} row — see the "Scope fix" note
     * inline below — or read/defaulted from {@code editInfo} for a header-only row), and, for a
     * {@code DETAIL_LINE} row, resolves the existing {@code Edited Line No.} into a
     * {@link DetailLineEditTarget}.
     *
     * @throws SkipException if {@code editInfo.getExistingVoucherNo()} has no {@code finvchmain} row
     */
    private MergedFinanceGeneralVoucherData buildOne(MasterTestData tc, List<EditVoucherInfo> editInfoList,
            List<ExpenseIncomeDetail> allDetailRows, List<SiteAllocation> allSiteRows,
            int invocationIndex, int totalInvocations) {

        String tcId = tc.getTestcaseid();
        EditVoucherInfo editInfo = editInfoList.stream()
                .filter(r -> r.getTestCaseId().equalsIgnoreCase(tcId))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "[ExcelGeneralVoucherEditDataSource] Missing EditVoucherInfo for: " + tcId));

        // No "Release Status" column (plan §16 — removed entirely, not merely downgraded to a
        // cross-check): EditGeneralVoucher's own "DB Pre-Check" step now reads the live
        // finvchmain.release_status directly, the only source of truth. A declarative sheet
        // value would go stale on its own the moment the Release-after-save dialog (§14)
        // fires Yes against this same repeatedly-run voucher.

        // Fetched once up front — regardless of which date branch below runs — so both branches,
        // plus the "Current Doc Date" report row (the voucher's ORIGINAL date), can read it
        // without a second DB round trip. Previously only fetched inside the blank-cell branch.
        Jdbi jdbi = DatabaseManager.getJdbi();
        FinVchMain header = jdbi.onDemand(FinVchMainDao.class)
                .findBySchemeDocno(editInfo.getExistingVoucherNo())
                .orElseThrow(() -> new SkipException("[ExcelGeneralVoucherEditDataSource] TestCaseID='" + tcId
                        + "' 'Existing Voucher No.'='" + editInfo.getExistingVoucherNo() + "' not found in finvchmain."));

        // Scope fix: a DETAIL_LINE row must edit ONLY the detail line — the header's
        // date/reference/remarks stay exactly as they were before this Save, regardless of what
        // the sheet's New Date/New Reference/New Remarks columns say. Reading those columns
        // unconditionally (the former behavior) silently turned every "detail-only" row into a
        // combined header+detail edit — the scope ambiguity the report review flagged. A row that
        // genuinely needs both changed at once belongs to a later regression-phase scenario, not
        // this one.
        boolean isDetailLineScenario = editInfo.isDetailLineScenario();

        // Field-isolated header edits (plan §4D): unlike DbGeneralVoucherEditDataSource's
        // enum-driven combos (which previously ALWAYS generated new reference/remarks/date via
        // FakerUtils together, needing the new HeaderFieldScope dimension to isolate one), an
        // Excel-authored row already isolates fields naturally — leaving a "New Reference"/
        // "New Remarks" cell blank passes null straight through below, and headerEdit()'s
        // addIfChanged already classifies an unchanged field as MUST_NOT_CHANGE (it was never
        // hardcoded to assume every header field changes). So no HeaderFieldScope column/hookup
        // is needed here — a field-isolated scenario is just a hand-authored row with two of the
        // three New-* cells left blank.

        LocalDate newDate;
        String newReference;
        String newRemarks;

        if (isDetailLineScenario) {
            newDate = header.getVchdt();
            newReference = header.getRefno();
            newRemarks = header.getNartext();
        } else {
            if (editInfo.getNewDate() != null) {
                newDate = editInfo.getNewDate();
                if (newDate.isAfter(LocalDate.now())) {
                    // Confirmed live: the app ACCEPTS a future-dated document within the same
                    // accounting year — no longer skipped. Just logged for visibility, since a
                    // future date here is unusual enough in a hand-authored sheet to be worth
                    // calling out (e.g. a stale placeholder date), even though it's a valid case.
                    Log.info("[ExcelGeneralVoucherEditDataSource] TestCaseID='{}' 'New Date' cell ({}) is in the "
                            + "future — proceeding, the app is confirmed to accept future-dated documents for "
                            + "General Voucher Edit within the same accounting year.", tcId, newDate);
                }
            } else {
                // Blank cell — resolve an accounting-year-aware date instead of blindly defaulting to
                // LocalDate.now() (that unconditional default is what caused a live save failure once
                // the existing voucher's own accounting year didn't bracket today — see the "GV Edit:
                // accounting-year-aware dates..." plan). RANDOM_MID is the "typical", always-valid
                // choice; Excel mode has no per-row date-variant dimension the way the DB matrix does.
                long ouCode = AccountingPeriodResolver.resolveOuCode(jdbi, header.getAdmsiteCodeOwner());
                newDate = AccountingPeriodResolver.resolveEditDate(
                        jdbi, header.getYcode(), ouCode, VoucherEditDateVariant.RANDOM_MID, PeriodDomain.FINANCE);
            }
            newReference = editInfo.getNewReference();
            newRemarks = editInfo.getNewRemarks();
        }

        DocumentInformation newDocInfo = new DocumentInformation(
                tcId,
                null,                              // voucherno — left null so setDocScheme()'s blank-guard
                                                   // returns immediately without ever touching/reading the
                                                   // Voucher No. field (confirmed via a live run: passing the
                                                   // existing scheme_docno instead — expecting the "already
                                                   // matches, skip" branch — still requires reading the
                                                   // field's current value first, and that read timed out
                                                   // waiting for visibility; Voucher No. is genuinely
                                                   // disabled/differently-rendered in Edit mode).
                newDate,
                newReference,
                null,                              // cashbank — unchanged on Edit, never re-selected
                newRemarks);

        List<ExpenseIncomeDetail> existingRows = allDetailRows.stream()
                .filter(r -> tcId.equalsIgnoreCase(r.getTestCaseID()))
                .collect(Collectors.toList());
        List<SiteAllocation> existingSiteRows = allSiteRows.stream()
                .filter(r -> tcId.equalsIgnoreCase(r.getTestCaseID()))
                .collect(Collectors.toList());

        var builder = MergedFinanceGeneralVoucherData.builder()
                .masterTestData(tc)
                .existingVoucherNo(editInfo.getExistingVoucherNo())
                .currentDocDate(header.getVchdt())
                .originalReference(header.getRefno())
                .originalRemarks(header.getNartext())
                .operationMode(GeneralVoucherOperationMode.EDIT)
                .documentInformation(newDocInfo)
                .expenseIncomeDetailList(existingRows)
                .siteAllocationList(existingSiteRows)
                .entryMethod(parseEntryMethod(editInfo.getEntryMethod(), tcId))
                .invocationIndex(invocationIndex)
                .totalInvocations(totalInvocations);

        if (editInfo.isDetailLineScenario()) {
            ExpenseIncomeDetail existingLine = existingRows.stream()
                    .filter(r -> editInfo.getEditedLineNo() != null
                            && editInfo.getEditedLineNo().equalsIgnoreCase(r.getLineNo()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException(
                            "[ExcelGeneralVoucherEditDataSource] TestCaseID='" + tcId + "' Edit Scenario=DETAIL_LINE "
                            + "but 'Edited Line No.'='" + editInfo.getEditedLineNo() + "' matches no row in the "
                            + "ExpenseIncomeDetail sheet for this TestCaseID."));

            boolean debit = existingLine.isDebit();
            // "New Ledger Narration" is optional — blank/absent keeps the existing row's
            // narration unchanged (the pre-existing V1 behavior); a non-blank cell lets a
            // scenario exercise the per-line nartext MUST_CHANGE assertion added for the
            // "Fix finpost/finvchdet narration-change detection" plan.
            String newLineNarration = (editInfo.getNewLedgerNarration() != null && !editInfo.getNewLedgerNarration().isBlank())
                    ? editInfo.getNewLedgerNarration()
                    : existingLine.getLedgerNarration();
            ExpenseIncomeDetail newLineValues = new ExpenseIncomeDetail(
                    tcId,
                    existingLine.getLineNo(),
                    existingLine.getGeneralLedger(),
                    existingLine.getSubLedger(),
                    existingLine.getSubLedgerId(),
                    newLineNarration,
                    editInfo.newIsReversalYes() ? "Yes" : "No",
                    debit ? editInfo.getNewAmount() : null,
                    debit ? null : editInfo.getNewAmount());

            // Excel mode only ever models the original IN_PLACE shape — the sheet has no column
            // for GL-type transition / add-new-line / site-split (those are DB-mode-only, plan
            // §4B), so this builder call needs no shape/beforeSiteAllocations wiring.
            builder.editedDetailLine(DetailLineEditTarget.builder()
                    .lineNo(existingLine.getLineNo())
                    .beforeRow(existingLine)
                    .newValues(newLineValues)
                    .shape(DetailLineEditTarget.EditShape.IN_PLACE)
                    .build());
        }

        return builder.build();
    }

    /**
     * Parses the {@code EditVoucherInfo} sheet's {@code Entry Method} cell — blank or
     * unrecognized defaults to {@code ACTIONS_EDIT} with a warning, not a thrown error (unlike
     * the future-date check, this isn't a hard business rule — plan §9.5).
     */
    private static VoucherEditEntryMethod parseEntryMethod(String raw, String tcId) {
        if (raw == null || raw.isBlank()) {
            return VoucherEditEntryMethod.ACTIONS_EDIT;
        }
        try {
            return VoucherEditEntryMethod.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            Log.warn("[ExcelGeneralVoucherEditDataSource] TestCaseID='{}' has unrecognized 'Entry Method' value '{}' "
                    + "— defaulting to ACTIONS_EDIT", tcId, raw);
            return VoucherEditEntryMethod.ACTIONS_EDIT;
        }
    }
}
