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
import com.gsl.pages.Finance.Operations.ArVoucherAdd;
import com.gsl.pages.Finance.Operations.ArVoucherListPage;
import com.gsl.pages.HomePage;
import com.gsl.pages.enums.Finance.MainMenuType;
import com.gsl.pages.enums.Finance.Operations.OperationsMenuType;
import com.gsl.pages.enums.topmenucomponent.MainModuleType;
import com.gsl.reports.ActualResultsHolder;
import com.gsl.reports.ReportLogger;
import com.gsl.reports.StepExecutor;
import com.gsl.testdata.UserCredential;
import com.gsl.testdata.dto.MergedFinanceArVoucherData;
import com.gsl.utils.Log;
import com.gsl.utils.NavigationUtils;
import com.tests.WebTest;
import org.testng.SkipException;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.List;

import static com.gsl.utils.ui.UiFieldState.EDITABLE;
import static com.gsl.validation.ui.UiValidationLevel.CONTRACT;

/**
 * UI-contract validation of the AR Voucher Add and Edit forms — the AR counterpart of
 * {@code GeneralVoucherUiValidation}. Driven through the same reusable component validators on
 * {@code VoucherDocumentInformationBase}/{@code VoucherFooterBase}/
 * {@code VoucherSubLedgerInformationBase}, inherited for free since {@code ARVoucherDocumentInformation}/
 * {@code ARVoucherFooter}/{@code ARVoucherSubLedgerInformation} already extend them — orchestrated
 * by the new {@code ArVoucherAdd#validateUiContract}.
 *
 * <p><b>Deliberately NOT routed through {@code MergedFinArVoucherDataSupplier}</b> — same reasoning
 * as {@code GeneralVoucherUiValidation}: that supplier's {@code switch (method.getName())} only
 * recognizes {@code editArVoucher}/{@code deleteArVoucher}, anything else falls through to the
 * Create-combination-matrix data source. Each method below gets its own tiny, plain
 * {@code @DataProvider} instead.
 *
 * <p><b>Edit-mode data: DB only.</b> {@code EditArVoucher} itself is DB-mode only (its own Javadoc:
 * "testdata.source=DB in config.properties", "At least one row in finvchmain with vchtype='R'") —
 * AR has no Excel-mode Edit data source to reuse at all (unlike GV/AP). This check doesn't
 * introduce one either; running it with {@code testdata.source=EXCEL} skips with a clear message
 * pointing at DB mode, rather than silently fabricating a new Excel artifact for a lightweight
 * UI-chrome check.
 *
 * <p><b>Scope note:</b> no per-row/cell Sub-Ledger Information grid validation — only the grid's
 * headers ({@code validateSubLedgerGridHeaderUi}/{@code validateReferenceSiteGridHeaderUi}), both
 * already free from the shared base — see the framework plan's "Scope note common to both" for
 * AR/AP. "View Mode"/{@code UiFieldState.READONLY} is likewise not exercised here for the same
 * reason as GV's own test class (no confirmed live path to a genuinely read-only AR Edit form).
 */
@ApiFlow(FinanceApiPattern.AR_VOUCHER)
public class ArVoucherUiValidation extends WebTest {

    @DataProvider(name = "ArVoucherUiAddData")
    public Object[][] addFormUiData() {
        MergedFinanceArVoucherData placeholder = MergedFinanceArVoucherData.builder()
                .subLedgerInformationList(List.of())
                .selectDocumentList(List.of())
                .paymentInformationList(List.of())
                .invocationIndex(1)
                .totalInvocations(1)
                .build();
        return new Object[][]{{placeholder}};
    }

    @DataProvider(name = "ArVoucherUiEditData")
    public Object[][] editFormUiData() {
        String source = ConfigFactory.getConfig().testDataSource();
        if (!"DB".equalsIgnoreCase(source)) {
            throw new SkipException(
                    "[ArVoucherUiValidation] testdata.source=" + source + " — AR Voucher Edit has no "
                    + "Excel-mode data source (see EditArVoucher's own Javadoc: DB-mode only). Switch "
                    + "testdata.source=DB to run validateEditFormUiContract.");
        }
        FinVchMain header = DatabaseManager.getJdbi().onDemand(FinVchMainDao.class)
                .findMostRecentByVoucherType(VoucherType.AR.code())
                .orElseThrow(() -> new SkipException(
                        "[ArVoucherUiValidation] No AR Voucher (vchtype='" + VoucherType.AR.code()
                        + "') found in the database — cannot resolve an existing voucher to open for "
                        + "the Edit-form UI-contract check."));
        Log.info("[ArVoucherUiValidation] Resolved existingVoucherNo='{}' for Edit-form UI check",
                header.getSchemeDocno());
        MergedFinanceArVoucherData row = MergedFinanceArVoucherData.builder()
                .existingVoucherNo(header.getSchemeDocno())
                .subLedgerInformationList(List.of())
                .selectDocumentList(List.of())
                .paymentInformationList(List.of())
                .invocationIndex(1)
                .totalInvocations(1)
                .build();
        return new Object[][]{{row}};
    }

    @FrameworkAnnotation(value = TestAuthor.ARANI, module = MainModuleType.FINANCE, subModule = "FinanceArVoucher", operation = AppOperation.NONE)
    @TestCaseId("FIN-AR-UI-ADD")
    @Test(description = "UI-contract validation of the AR Voucher Add form",
            dataProvider = "ArVoucherUiAddData")
    public void validateAddFormUiContract(MergedFinanceArVoucherData data) {
        ActualResultsHolder.get().setVoucherType("AR");

        final HomePage[]           homeRef = new HomePage[1];
        final ArVoucherListPage[] listRef  = new ArVoucherListPage[1];
        final ArVoucherAdd[]      addRef   = new ArVoucherAdd[1];

        StepExecutor.step("Login to Application", () -> {
            UserCredential u = DriverManager.getCurrentUser();
            homeRef[0] = SessionManager.ensureLoggedIn(u);
            ReportLogger.business("Logged in as: " + u.username());
        });

        StepExecutor.step("Open Finance → AR Voucher List", () -> listRef[0] =
                NavigationUtils.navigateToFinanceSideMenu(
                        homeRef[0], MainMenuType.OPERATIONS, OperationsMenuType.ARVOUCHER,
                        ArVoucherListPage.class));

        StepExecutor.step("Open Add Form", () -> addRef[0] = listRef[0].clickAdd());

        StepExecutor.step("Validate Add Form UI Contract", () ->
                addRef[0].validateUiContract(CONTRACT, EDITABLE, false));

        StepExecutor.step("Close Add Form (discard)", () -> {
            listRef[0] = addRef[0].close().clickYes(ArVoucherListPage::new);
            ReportLogger.business("Add form closed (discarded), returned to list");
        });

        // UI validation is soft: every check above ran and was reported; fail the test once, here.
        StepExecutor.step("Finalize UI assertions", com.gsl.validation.ui.UiFailureCollector::assertNoPending);
    }

    @FrameworkAnnotation(value = TestAuthor.ARANI, module = MainModuleType.FINANCE, subModule = "FinanceArVoucher", operation = AppOperation.NONE)
    @TestCaseId("FIN-AR-UI-EDIT")
    @Test(description = "UI-contract validation of the AR Voucher Edit form",
            dataProvider = "ArVoucherUiEditData")
    public void validateEditFormUiContract(MergedFinanceArVoucherData data) {
        ActualResultsHolder.get().setVoucherType("AR");
        ActualResultsHolder.get().setTestData(data);

        final HomePage[]           homeRef = new HomePage[1];
        final ArVoucherListPage[] listRef  = new ArVoucherListPage[1];
        final ArVoucherAdd[]      editRef  = new ArVoucherAdd[1];

        StepExecutor.step("Login to Application", () -> {
            UserCredential u = DriverManager.getCurrentUser();
            homeRef[0] = SessionManager.ensureLoggedIn(u);
            ReportLogger.business("Logged in as: " + u.username());
        });

        StepExecutor.step("Open Finance → AR Voucher List", () -> listRef[0] =
                NavigationUtils.navigateToFinanceSideMenu(
                        homeRef[0], MainMenuType.OPERATIONS, OperationsMenuType.ARVOUCHER,
                        ArVoucherListPage.class));

        StepExecutor.step("Clear Filter and Refresh List", () ->
                listRef[0].clearFilterAndRefreshList().resetToFirstPageIfEnabled());

        StepExecutor.step("Open Existing Voucher for Edit", () -> {
            editRef[0] = listRef[0].doubleClickToEditFast(data.getExistingVoucherNo());
            editRef[0].verifyEditFormFullyLoaded(data.getExistingVoucherNo());
        });

        StepExecutor.step("Validate Edit Form UI Contract", () ->
                editRef[0].validateUiContract(CONTRACT, EDITABLE, true));

        StepExecutor.step("Close Edit Form (discard)", () -> {
            listRef[0] = editRef[0].close().clickYes(ArVoucherListPage::new);
            ReportLogger.business("Edit form closed (discarded), returned to list");
        });

        // UI validation is soft: every check above ran and was reported; fail the test once, here.
        StepExecutor.step("Finalize UI assertions", com.gsl.validation.ui.UiFailureCollector::assertNoPending);
    }
}
