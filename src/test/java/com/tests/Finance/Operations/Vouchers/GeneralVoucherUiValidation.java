package com.tests.Finance.Operations.Vouchers;

import com.gsl.annotations.ApiFlow;
import com.gsl.annotations.FrameworkAnnotation;
import com.gsl.annotations.TestCaseId;
import com.gsl.config.ConfigFactory;
import com.gsl.database.DatabaseManager;
import com.gsl.database.dao.finance.FinVchMainDao;
import com.gsl.driver.DriverManager;
import com.gsl.driver.SessionManager;
import com.gsl.enums.AppOperation;
import com.gsl.enums.TestAuthor;
import com.gsl.enums.VoucherType;
import com.gsl.model.finance.FinVchMain;
import com.gsl.network.pattern.FinanceApiPattern;
import com.gsl.pages.Finance.Operations.GeneralVoucherAdd;
import com.gsl.pages.Finance.Operations.GeneralVoucherListPage;
import com.gsl.pages.HomePage;
import com.gsl.pages.enums.Finance.MainMenuType;
import com.gsl.pages.enums.Finance.Operations.OperationsMenuType;
import com.gsl.pages.enums.topmenucomponent.MainModuleType;
import com.gsl.reports.ActualResultsHolder;
import com.gsl.reports.ReportLogger;
import com.gsl.reports.StepExecutor;
import com.gsl.testdata.Finance.Operations.Vouchers.GeneralVoucher.DocumentInformation;
import com.gsl.testdata.Finance.Operations.Vouchers.GeneralVoucher.EditVoucherInfo;
import com.gsl.testdata.Finance.Operations.Vouchers.GeneralVoucher.ExpenseIncomeDetail;
import com.gsl.testdata.UserCredential;
import com.gsl.testdata.dto.MergedFinanceGeneralVoucherData;
import com.gsl.utils.Log;
import com.gsl.utils.NavigationUtils;
import com.gsl.validation.ui.UiValidationFailure;
import com.tests.WebTest;
import io.github.sskorol.data.XlsxReader;
import org.assertj.core.api.SoftAssertions;
import org.testng.SkipException;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static io.github.sskorol.data.TestDataReader.use;
import static com.gsl.utils.ui.UiFieldState.EDITABLE;
import static com.gsl.validation.ui.UiValidationLevel.CONTRACT;

/**
 * UI-contract validation (fonts, colors, dimensions, alignment, required-field markers, component
 * states, grid presentation — see {@code Ginesys_UI_Validation_Framework_General_Voucher.md} and
 * this framework's own implementation plan) for the General Voucher Add and Edit forms.
 *
 * <p>Driven through the reusable component validators added to
 * {@code VoucherDocumentInformationBase}/{@code VoucherFooterBase}/
 * {@code VoucherSubLedgerInformationBase} (shared with AR/AP — see each class's own "UI-contract
 * validation" section) plus the GV-only fields on
 * {@code GeneralVoucherDocumentInformation}/{@code GeneralVoucherExpenseIncomeDetail}, orchestrated
 * by {@code GeneralVoucherAdd#validateUiContract}.
 *
 * <h3>Deliberately NOT routed through {@code MergedFinGeneralVoucherDataSupplier}</h3>
 * That supplier's {@code switch (method.getName())} — and, one layer deeper,
 * {@code GeneralVoucherFixtureFactory.scenariosFor(methodName)} for Create and
 * {@code DbGeneralVoucherEditDataSource}'s own hard-coded method-name allowlist for Edit — only
 * recognize the real business-flow test methods. An unrecognized method name silently gets 0 rows
 * (Create path) or a thrown {@code SkipException} (Edit path) — confirmed live (run
 * 20260930_221729): both methods here got "Emitting 0 ... scenario row(s)" and were never actually
 * invoked. Rather than punch a new hole through three layers of gating built for a much heavier
 * combination-matrix pipeline this check doesn't need, each method below gets its own tiny, plain
 * TestNG {@code @DataProvider} — the same "skip the DataSupplier machinery entirely" convention
 * {@code CreateGeneralVoucherNegativeBoundary} already established for its own 5 scenarios (see
 * that class's Javadoc: {@code WebTest.setUp()} borrows a pooled/logged-in session correctly even
 * with no explicit {@code UserCredential} parameter).
 *
 * <p><b>"View Mode" / read-only-field coverage — not yet exercised here:</b> Ginesys's own Edit
 * flow always un-posts a Posted voucher before opening it for Edit (see {@code EditGeneralVoucher}'s
 * "Unpost if Posted" step) — there is currently no confirmed live path that opens a genuinely
 * read-only/Posted General Voucher form. {@link #validateEditFormUiContract} therefore validates
 * the Edit form in its one confirmed-reachable state, {@code UiFieldState.EDITABLE}.
 * {@code UiFieldState.READONLY} remains fully implemented in the framework (see
 * {@code UiComponentValidator#validateFieldState}) for whichever real path — a genuine View-only
 * screen, or a permission-restricted user — turns out to reach it; wire a third test method here
 * once one is confirmed.
 *
 * <p><b>Placeholder values:</b> every {@code FieldStyleSpec} constant this run checks against is
 * currently a placeholder sentinel, not a live-confirmed CSS value (per explicit instruction — see
 * {@code FieldStyleSpec}'s own Javadoc) — every property-level assertion here is EXPECTED to
 * soft-fail on first run. The resulting failures (expected placeholder vs. actual real value, one
 * per property) are what drive filling in the true values.
 */
@ApiFlow(FinanceApiPattern.GENERAL_VOUCHER)
public class GeneralVoucherUiValidation extends WebTest {

    /** No business data needed at all — the Add form starts blank. One trivial row is enough to
     *  make TestNG invoke the test method once. */
    @DataProvider(name = "GeneralVoucherUiAddData")
    public Object[][] addFormUiData() {
        MergedFinanceGeneralVoucherData placeholder = MergedFinanceGeneralVoucherData.builder()
                .expenseIncomeDetailList(List.of())
                .siteAllocationList(List.of())
                .invocationIndex(1)
                .totalInvocations(1)
                .build();
        return new Object[][]{{placeholder}};
    }

    /** Needs exactly one thing — an existing voucher's {@code scheme_docno} — nothing about its
     *  business shape (GL type, site/sub-ledger shape, payment variant), so this resolves it
     *  directly rather than through the combination-matrix Edit data source. Honors
     *  {@code testdata.source=EXCEL|DB} like every other GV test. */
    @DataProvider(name = "GeneralVoucherUiEditData")
    public Object[][] editFormUiData() {
        String source = ConfigFactory.getConfig().testDataSource();
        String existingVoucherNo = "DB".equalsIgnoreCase(source) ? resolveVoucherNoFromDb() : resolveVoucherNoFromExcel();
        Log.info("[GeneralVoucherUiValidation] Resolved existingVoucherNo='{}' (source={}) for Edit-form UI check",
                existingVoucherNo, source);
        MergedFinanceGeneralVoucherData row = MergedFinanceGeneralVoucherData.builder()
                .existingVoucherNo(existingVoucherNo)
                .expenseIncomeDetailList(List.of())
                .siteAllocationList(List.of())
                .invocationIndex(1)
                .totalInvocations(1)
                .build();
        return new Object[][]{{row}};
    }

    /** Most recent NON-released {@code vchtype='G'} row (see
     *  {@code FinVchMainDao#findMostRecentByVoucherType}'s own Javadoc — a Released voucher blocks
     *  double-click-edit with Ginesys's own "document is released" alert instead of opening the
     *  Edit form, confirmed live). This check only opens the form and reads computed styles, it
     *  never mutates anything, so it doesn't need the claim/backfill/closed-period machinery
     *  {@code DbGeneralVoucherEditDataSource} carries for scenarios that DO edit real field
     *  values. */
    private static String resolveVoucherNoFromDb() {
        FinVchMain header = DatabaseManager.getJdbi().onDemand(FinVchMainDao.class)
                .findMostRecentByVoucherType(VoucherType.GENERAL.code())
                .orElseThrow(() -> new SkipException(
                        "[GeneralVoucherUiValidation] No General Voucher (vchtype='"
                        + VoucherType.GENERAL.code() + "') found in the database — cannot resolve an "
                        + "existing voucher to open for the Edit-form UI-contract check."));
        return header.getSchemeDocno();
    }

    /** Reuses the SAME sheet/column {@code EditGeneralVoucher} already depends on
     *  ({@code EditVoucherInfo}'s "Existing Voucher No." column) — takes whichever row's voucher
     *  number is already configured there (no MasterTestData/TestCaseID join needed, since this
     *  check doesn't care about test-case description matching, only "a real voucher number"). */
    private static String resolveVoucherNoFromExcel() {
        List<EditVoucherInfo> rows = use(XlsxReader.class)
                .withTarget(EditVoucherInfo.class)
                .withSource(ConfigFactory.getExcelDataConfig().generalVoucherPath())
                .read().toList();
        String voucherNo = rows.isEmpty() ? null : rows.get(0).getExistingVoucherNo();
        if (voucherNo == null || voucherNo.isBlank()) {
            throw new SkipException(
                    "[GeneralVoucherUiValidation] No 'EditVoucherInfo' row with an 'Existing Voucher No.' "
                    + "found in GeneralVoucher.xlsx — cannot resolve an existing voucher to open for the "
                    + "Edit-form UI-contract check. See EditVoucherInfo.java's Javadoc.");
        }
        return voucherNo;
    }

    @FrameworkAnnotation(value = TestAuthor.ARANI, module = MainModuleType.FINANCE, subModule = "FinanceGenVoucher", operation = AppOperation.NONE)
    @TestCaseId("FIN-GV-UI-ADD")
    @Test(description = "UI-contract validation of the General Voucher Add form",
            dataProvider = "GeneralVoucherUiAddData")
    public void validateAddFormUiContract(MergedFinanceGeneralVoucherData data) {
        ActualResultsHolder.get().setVoucherType("GV");

        final HomePage[]               homeRef = new HomePage[1];
        final GeneralVoucherListPage[] listRef  = new GeneralVoucherListPage[1];
        final GeneralVoucherAdd[]      addRef   = new GeneralVoucherAdd[1];

        StepExecutor.step("Login to Application", () -> {
            UserCredential u = DriverManager.getCurrentUser();
            homeRef[0] = SessionManager.ensureLoggedIn(u);
            ReportLogger.business("Logged in as: " + u.username());
        });

        StepExecutor.step("Open Finance → General Voucher List", () -> listRef[0] =
                NavigationUtils.navigateToFinanceSideMenu(
                        homeRef[0], MainMenuType.OPERATIONS, OperationsMenuType.CATEGORY,
                        GeneralVoucherListPage.class));

        StepExecutor.step("Open Add Form", () -> addRef[0] = listRef[0].clickAdd());

        StepExecutor.step("Validate Add Form UI Contract", () ->
                addRef[0].validateUiContract(CONTRACT, EDITABLE, false));

        StepExecutor.step("Close Add Form (discard)", () -> {
            listRef[0] = addRef[0].close().clickYes(GeneralVoucherListPage::new);
            ReportLogger.business("Add form closed (discarded), returned to list");
        });

        // UI validation is soft: every check above ran and was reported; fail the test once, here.
        StepExecutor.step("Finalize UI assertions", com.gsl.validation.ui.UiFailureCollector::assertNoPending);
    }

    // ── Full phased Add-form flow: blank → Cash/Bank → rows with Allocate Sites / Payment ──────

    /** Cash/Bank selected between the blank-form and row phases — a real, already-proven value
     *  from this repo's own GeneralVoucher.xlsx (same sourcing as the Net Amount test below). */
    /** Replaced after login by a GL / Bank ledger that exists for the logged-in user (see resolveTestData). */
    private static final String GL_PLACEHOLDER = "(resolved after login)";
    private static final String PROVEN_TYPE1_GL = "Advance Income Tax";

    /** Three rows so every applicability shape appears in one report:
     *  <ol>
     *    <li>Type1 GL, Dr 100.00, WITH a NEFT/RTGS payment, no Site (Allocate Sites = Skip)</li>
     *    <li>Type2 (Site-only) GL resolved from the DB, Dr 2.00 split across up to two of that GL's
     *        own configured sites, no payment (Payment Information = Skip)</li>
     *    <li>Type1 GL, Cr 50.00, neither (both Skip)</li>
     *  </ol>
     *  Not a business scenario — Save is never clicked, so the voucher need not balance. The Type2
     *  GL and its sites come from {@code fingl}/{@code fingl_site}/{@code admsite} (first match), so
     *  no hard-coded environment-specific names. */
    @DataProvider(name = "GeneralVoucherUiFullFlowData")
    public Object[][] fullFlowUiData() {
        // Row 2 (Site-only GL + its Allocate Sites rows) is resolved AFTER login by
        // resolveUserData(...): which sites the picker offers depends on the logged-in user (assigned sites,
        // same Organizational Unit as the owner site), which no data provider can know.
        String row2 = "2";
        com.gsl.testdata.Finance.Operations.Vouchers.GeneralVoucher.PaymentInformation payment =
                new com.gsl.testdata.Finance.Operations.Vouchers.GeneralVoucher.PaymentInformation(
                        "FIN-GV-UI-ADD-FLOW", "CASHBANK", com.gsl.enums.PaymentSelectionType.NEFT_RTGS,
                        com.gsl.utils.payment.PaymentReferenceGenerator.generateNEFT(),
                        LocalDate.now().format(com.gsl.utils.DateUtils.UI_DATE_FORMAT), null, null);

        List<com.gsl.pages.pagecomponent.Finance.Operations.GeneralVoucher.GeneralVoucherExpenseIncomeDetail.RowUiPlan> plans = List.of(
                new com.gsl.pages.pagecomponent.Finance.Operations.GeneralVoucher.GeneralVoucherExpenseIncomeDetail.RowUiPlan(
                        new ExpenseIncomeDetail("FIN-GV-UI-ADD-FLOW", "1", GL_PLACEHOLDER, null, null,
                                "UI validation — row 1", "No", new BigDecimal("100"), null),
                        List.of(), payment, false),
                siteOnlyRowPlaceholder(row2, null),
                new com.gsl.pages.pagecomponent.Finance.Operations.GeneralVoucher.GeneralVoucherExpenseIncomeDetail.RowUiPlan(
                        new ExpenseIncomeDetail("FIN-GV-UI-ADD-FLOW", "3", GL_PLACEHOLDER, null, null,
                                "UI validation — row 3", "No", null, new BigDecimal("50")),
                        List.of(), null, false));
        return new Object[][]{{plans}};
    }

    /** The row with its placeholder GL swapped for {@code gl}; any other row is returned unchanged. */
    private static ExpenseIncomeDetail withGl(ExpenseIncomeDetail r, String gl) {
        if (!GL_PLACEHOLDER.equals(r.getGeneralLedger())) return r;
        return new ExpenseIncomeDetail(r.getTestCaseID(), r.getLineNo(), gl, r.getSubLedger(), r.getSubLedgerId(),
                r.getLedgerNarration(), r.getIsReversal(), r.getExpenseDr(), r.getIncomeCr());
    }

    /** Test data that depends on WHO is logged in, resolved from the DB after login. */
    private record UserData(String cashBank,
            List<com.gsl.pages.pagecomponent.Finance.Operations.GeneralVoucher.GeneralVoucherExpenseIncomeDetail.RowUiPlan> plans) {}

    private static com.gsl.pages.pagecomponent.Finance.Operations.GeneralVoucher.GeneralVoucherExpenseIncomeDetail.RowUiPlan
            siteOnlyRowPlaceholder(String lineNo, Object unused) {
        return siteOnlyRow(lineNo, "(resolved after login)", List.of());
    }

    private static com.gsl.pages.pagecomponent.Finance.Operations.GeneralVoucher.GeneralVoucherExpenseIncomeDetail.RowUiPlan
            siteOnlyRow(String lineNo, String glName, List<com.gsl.testdata.Finance.Operations.Vouchers.GeneralVoucher.SiteAllocation> allocations) {
        return new com.gsl.pages.pagecomponent.Finance.Operations.GeneralVoucher.GeneralVoucherExpenseIncomeDetail.RowUiPlan(
                new ExpenseIncomeDetail("FIN-GV-UI-ADD-FLOW", lineNo, glName, null, null,
                        "UI validation — row 2", "No", new BigDecimal("2"), null),
                allocations, null, false);
    }

    /** Resolves everything that depends on the logged-in user: the Type1 GL and Bank ledger, and the Site-only
     *  GL with sites the Allocate Sites picker offers this user. */
    private static UserData resolveUserData(List<com.gsl.pages.pagecomponent.Finance.Operations.GeneralVoucher.GeneralVoucherExpenseIncomeDetail.RowUiPlan> plans) {
        var type1 = com.gsl.utils.dataprovider.source.finance.DbGeneralVoucherDataSource
                .resolveType1GlAndBankForCurrentUser(PROVEN_TYPE1_GL);
        type1.narrative().forEach(ReportLogger::business);
        var resolved = com.gsl.utils.dataprovider.source.finance.DbGeneralVoucherDataSource.resolveSiteOnlyGlForCurrentUser(2);
        resolved.narrative().forEach(ReportLogger::business);
        BigDecimal each = resolved.siteNames().size() == 2 ? new BigDecimal("1.00") : new BigDecimal("2.00");
        List<com.gsl.testdata.Finance.Operations.Vouchers.GeneralVoucher.SiteAllocation> allocations =
                resolved.siteNames().stream().map(n -> {
                    var a = new com.gsl.testdata.Finance.Operations.Vouchers.GeneralVoucher.SiteAllocation();
                    a.setTestCaseID("FIN-GV-UI-ADD-FLOW");
                    a.setLineNo("2");
                    a.setSiteName(n);
                    a.setAmount(each);
                    return a;
                }).toList();
        List<com.gsl.pages.pagecomponent.Finance.Operations.GeneralVoucher.GeneralVoucherExpenseIncomeDetail.RowUiPlan> out = new java.util.ArrayList<>();
        for (int i = 0; i < plans.size(); i++) {
            com.gsl.pages.pagecomponent.Finance.Operations.GeneralVoucher.GeneralVoucherExpenseIncomeDetail.RowUiPlan p = plans.get(i);
            out.add(i == 1 ? siteOnlyRow("2", resolved.glName(), allocations)
                    : new com.gsl.pages.pagecomponent.Finance.Operations.GeneralVoucher.GeneralVoucherExpenseIncomeDetail.RowUiPlan(withGl(p.row(), type1.glName()), p.allocations(), p.payment(), p.paymentDebit()));
        }
        return new UserData(type1.cashBankName(), out);
    }

    @FrameworkAnnotation(value = TestAuthor.ARANI, module = MainModuleType.FINANCE, subModule = "FinanceGenVoucher", operation = AppOperation.NONE)
    @TestCaseId("FIN-GV-UI-ADD-FLOW")
    @Test(description = "Phased UI validation of the General Voucher Add form: blank form, after Cash/Bank, "
            + "each grid row with its Allocate Sites / Payment Information popups, grid footer and Net Amount "
            + "checkpoints after every row, page footer",
            dataProvider = "GeneralVoucherUiFullFlowData")
    public void validateAddFormFullFlowUi(
            List<com.gsl.pages.pagecomponent.Finance.Operations.GeneralVoucher.GeneralVoucherExpenseIncomeDetail.RowUiPlan> plans) {
        ActualResultsHolder.get().setVoucherType("GV");

        final HomePage[]               homeRef = new HomePage[1];
        final GeneralVoucherListPage[] listRef  = new GeneralVoucherListPage[1];
        final GeneralVoucherAdd[]      addRef   = new GeneralVoucherAdd[1];

        StepExecutor.step("Login to Application", () -> {
            UserCredential u = DriverManager.getCurrentUser();
            homeRef[0] = SessionManager.ensureLoggedIn(u);
            ReportLogger.business("Logged in as: " + u.username());
        });

        final UserData[] userData = new UserData[1];
        StepExecutor.step("Resolve test data for this user", () -> userData[0] = resolveUserData(plans));

        StepExecutor.step("Open Finance → General Voucher List", () -> listRef[0] =
                NavigationUtils.navigateToFinanceSideMenu(
                        homeRef[0], MainMenuType.OPERATIONS, OperationsMenuType.CATEGORY,
                        GeneralVoucherListPage.class));

        StepExecutor.step("Open Add Form", () -> addRef[0] = listRef[0].clickAdd());

        StepExecutor.step("UI Validation", () ->
                addRef[0].validateAddFormFlowUi(CONTRACT, userData[0].cashBank(), userData[0].plans()));

        StepExecutor.step("Close Add Form (discard)", () -> {
            listRef[0] = addRef[0].close().clickYes(GeneralVoucherListPage::new);
            ReportLogger.business("Add form closed (discarded), returned to list");
        });

        // UI validation is soft: every check above ran and was reported; fail the test once, here.
        StepExecutor.step("Finalize UI assertions", com.gsl.validation.ui.UiFailureCollector::assertNoPending);
    }

    @FrameworkAnnotation(value = TestAuthor.ARANI, module = MainModuleType.FINANCE, subModule = "FinanceGenVoucher", operation = AppOperation.NONE)
    @TestCaseId("FIN-GV-UI-EDIT")
    @Test(description = "UI-contract validation of the General Voucher Edit form",
            dataProvider = "GeneralVoucherUiEditData")
    public void validateEditFormUiContract(MergedFinanceGeneralVoucherData data) {
        ActualResultsHolder.get().setVoucherType("GV");
        ActualResultsHolder.get().setTestData(data);

        final HomePage[]               homeRef = new HomePage[1];
        final GeneralVoucherListPage[] listRef  = new GeneralVoucherListPage[1];
        final GeneralVoucherAdd[]      editRef  = new GeneralVoucherAdd[1];

        StepExecutor.step("Login to Application", () -> {
            UserCredential u = DriverManager.getCurrentUser();
            homeRef[0] = SessionManager.ensureLoggedIn(u);
            ReportLogger.business("Logged in as: " + u.username());
        });

        StepExecutor.step("Open Finance → General Voucher List", () -> listRef[0] =
                NavigationUtils.navigateToFinanceSideMenu(
                        homeRef[0], MainMenuType.OPERATIONS, OperationsMenuType.CATEGORY,
                        GeneralVoucherListPage.class));

        StepExecutor.step("Clear Filter and Refresh List", () ->
                listRef[0].clearFilterAndRefreshList().resetToFirstPageIfEnabled());

        StepExecutor.step("Open Existing Voucher for Edit", () -> {
            editRef[0] = listRef[0].doubleClickToEditFast(data.getExistingVoucherNo());
            editRef[0].verifyEditFormFullyLoaded(data.getExistingVoucherNo());
        });

        StepExecutor.step("Validate Edit Form UI Contract", () ->
                editRef[0].validateUiContract(CONTRACT, EDITABLE, true));

        StepExecutor.step("Close Edit Form (discard)", () -> {
            listRef[0] = editRef[0].close().clickYes(GeneralVoucherListPage::new);
            ReportLogger.business("Edit form closed (discarded), returned to list");
        });

        // UI validation is soft: every check above ran and was reported; fail the test once, here.
        StepExecutor.step("Finalize UI assertions", com.gsl.validation.ui.UiFailureCollector::assertNoPending);
    }

    /** One expected Net Amount reading after a given row has been added — {@code amount} is the
     *  exact value Net Amount should show at that point, {@code label} is "Payment"/"Receipt". */
    private record NetAmountExpectation(BigDecimal amount, String label) {}

    /** User-confirmed business rule (2026-10-01): Net Amount = |Σ Expense(Dr) − Σ Income(Cr)|,
     *  labeled "Payment" when the Dr side dominates (net debit ⇒ money going out), "Receipt" when
     *  the Cr side dominates (net credit ⇒ money coming in) — checked after EACH row, not just at
     *  the end, since the app recomputes it incrementally. Both scenarios use
     *  {@code generalLedger="Advance Income Tax"} — a real, already-proven {@code TYPE1_NONE}
     *  (no Site/Sub-Ledger) GL from this repo's own {@code GeneralVoucher.xlsx} fixture — so
     *  {@link GeneralVoucherAdd#fillTypeOneNoneRow} can fill each row directly, with no DB/Excel
     *  {@code resolvedGlType} resolution needed. */
    @DataProvider(name = "GeneralVoucherNetAmountComputationData")
    public Object[][] netAmountComputationData() {
        List<ExpenseIncomeDetail> scenarioDrDominant = List.of(
                typeOneNoneRow("1", new BigDecimal("200"), null),
                typeOneNoneRow("2", null, new BigDecimal("100")));
        List<NetAmountExpectation> expectedDrDominant = List.of(
                new NetAmountExpectation(new BigDecimal("200.00"), "Payment"),
                new NetAmountExpectation(new BigDecimal("100.00"), "Payment"));

        List<ExpenseIncomeDetail> scenarioCrDominant = List.of(
                typeOneNoneRow("1", null, new BigDecimal("200")),
                typeOneNoneRow("2", null, new BigDecimal("100")));
        List<NetAmountExpectation> expectedCrDominant = List.of(
                new NetAmountExpectation(new BigDecimal("200.00"), "Receipt"),
                new NetAmountExpectation(new BigDecimal("300.00"), "Receipt"));

        return new Object[][]{
                {scenarioDrDominant, expectedDrDominant},
                {scenarioCrDominant, expectedCrDominant},
        };
    }

    private static ExpenseIncomeDetail typeOneNoneRow(String lineNo, BigDecimal expenseDr, BigDecimal incomeCr) {
        return new ExpenseIncomeDetail("FIN-GV-UI-NET-AMOUNT", lineNo, GL_PLACEHOLDER, null, null,
                "UI validation — Net Amount computation", "No", expenseDr, incomeCr);
    }

    @FrameworkAnnotation(value = TestAuthor.ARANI, module = MainModuleType.FINANCE, subModule = "FinanceGenVoucher", operation = AppOperation.NONE)
    @TestCaseId("FIN-GV-UI-NET-AMOUNT")
    @Test(description = "Net Amount is correctly absent on a blank Add form, then computes correctly "
            + "as Expense/Income Detail rows are added",
            dataProvider = "GeneralVoucherNetAmountComputationData")
    public void validateNetAmountComputationOnAdd(List<ExpenseIncomeDetail> rows,
            List<NetAmountExpectation> expectedAfterEachRow) {
        ActualResultsHolder.get().setVoucherType("GV");

        final HomePage[]               homeRef = new HomePage[1];
        final GeneralVoucherListPage[] listRef  = new GeneralVoucherListPage[1];
        final GeneralVoucherAdd[]      addRef   = new GeneralVoucherAdd[1];

        StepExecutor.step("Login to Application", () -> {
            UserCredential u = DriverManager.getCurrentUser();
            homeRef[0] = SessionManager.ensureLoggedIn(u);
            ReportLogger.business("Logged in as: " + u.username());
        });

        final String[] glAndBank = new String[2];
        StepExecutor.step("Resolve GL and Bank ledger for this user", () -> {
            var t1 = com.gsl.utils.dataprovider.source.finance.DbGeneralVoucherDataSource
                    .resolveType1GlAndBankForCurrentUser(PROVEN_TYPE1_GL);
            t1.narrative().forEach(ReportLogger::business);
            glAndBank[0] = t1.glName();
            glAndBank[1] = t1.cashBankName();
        });

        StepExecutor.step("Open Finance → General Voucher List", () -> listRef[0] =
                NavigationUtils.navigateToFinanceSideMenu(
                        homeRef[0], MainMenuType.OPERATIONS, OperationsMenuType.CATEGORY,
                        GeneralVoucherListPage.class));

        StepExecutor.step("Open Add Form", () -> addRef[0] = listRef[0].clickAdd());

        // Confirmed live (2026-10-01, user-stated business rule): the Expense/Income Detail grid
        // isn't genuinely editable — and Net Amount doesn't compute — until Cash/Bank is selected
        // first. "HDFC Bank" is a real, already-proven Cash/Bank value from this repo's own
        // GeneralVoucher.xlsx DocumentInformation sheet (GV_TC01) — same sourcing precedent as
        // "Advance Income Tax" for the GL, not a DB lookup (this test class deliberately bypasses
        // the heavier DB-resolution pipeline — see this class's own Javadoc).
        StepExecutor.step("Fill Cash/Bank", () -> addRef[0].fillDocumentHeader(
                new DocumentInformation(null, null, LocalDate.now(), null, glAndBank[1], null)));

        SoftAssertions soft = new SoftAssertions();

        StepExecutor.step("Verify Net Amount Absent Before Any Row", () ->
                addRef[0].getDocumentInformation().validateNetAmountUi(CONTRACT, false)
                        .stream().filter(UiValidationFailure::isFail)
                        .forEach(failure -> soft.fail(failure.toString())));

        for (int i = 0; i < rows.size(); i++) {
            final int rowIndex = i;
            ExpenseIncomeDetail row = withGl(rows.get(i), glAndBank[0]);
            NetAmountExpectation expected = expectedAfterEachRow.get(i);
            StepExecutor.step("Add Row " + (rowIndex + 1) + " and Verify Net Amount", () -> {
                addRef[0].fillTypeOneNoneRow(rowIndex, row);
                soft.assertThat(addRef[0].getDocumentInformation().getNetAmountValue())
                        .as("Net Amount value after row " + (rowIndex + 1))
                        .isEqualByComparingTo(expected.amount());
                soft.assertThat(addRef[0].getDocumentInformation().getNetAmountLabel())
                        .as("Net Amount label after row " + (rowIndex + 1))
                        .isEqualTo(expected.label());
            });
        }

        soft.assertAll();

        StepExecutor.step("Close Add Form (discard)", () -> {
            listRef[0] = addRef[0].close().clickYes(GeneralVoucherListPage::new);
            ReportLogger.business("Add form closed (discarded), returned to list");
        });
    }
}
