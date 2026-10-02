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
import com.gsl.pages.Finance.Operations.ApVoucherAdd;
import com.gsl.pages.Finance.Operations.ApVoucherListPage;
import com.gsl.pages.HomePage;
import com.gsl.pages.enums.Finance.MainMenuType;
import com.gsl.pages.enums.Finance.Operations.OperationsMenuType;
import com.gsl.pages.enums.topmenucomponent.MainModuleType;
import com.gsl.reports.ActualResultsHolder;
import com.gsl.reports.ReportLogger;
import com.gsl.reports.StepExecutor;
import com.gsl.testdata.Finance.Operations.Vouchers.APVoucher.ApEditVoucherInfo;
import com.gsl.testdata.UserCredential;
import com.gsl.testdata.dto.MergedFinanceApVoucherData;
import com.gsl.utils.Log;
import com.gsl.utils.NavigationUtils;
import com.tests.WebTest;
import io.github.sskorol.data.XlsxReader;
import org.testng.SkipException;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.List;

import static io.github.sskorol.data.TestDataReader.use;
import static com.gsl.utils.ui.UiFieldState.EDITABLE;
import static com.gsl.validation.ui.UiValidationLevel.CONTRACT;

/**
 * UI-contract validation of the AP Voucher Add and Edit forms — the AP counterpart of
 * {@code GeneralVoucherUiValidation}. Driven through the same reusable component validators on
 * {@code VoucherDocumentInformationBase}/{@code VoucherFooterBase}/
 * {@code VoucherSubLedgerInformationBase}, inherited for free since {@code APVoucherDocumentInformation}/
 * {@code APVoucherFooter}/{@code APVoucherSubLedgerInformation} already extend them, plus AP's own
 * On Account radio ({@code APVoucherDocumentInformation#validateOnAccountUi}) — orchestrated by the
 * new {@code ApVoucherAdd#validateUiContract}.
 *
 * <p><b>Deliberately NOT routed through {@code MergedFinApVoucherDataSupplier}</b> — same reasoning
 * as {@code GeneralVoucherUiValidation}/{@code ArVoucherUiValidation}: that supplier's
 * {@code switch (method.getName())} only recognizes the real business-flow methods. Each method
 * below gets its own tiny, plain {@code @DataProvider} instead.
 *
 * <p><b>Edit-mode data, both modes supported</b> (unlike AR, which is DB-only): DB mode resolves
 * the most recent {@code vchtype='P'} voucher directly (bypassing
 * {@code DbApVoucherEditDataSource}'s combination-matrix/claim machinery, which this check doesn't
 * need); Excel mode reads {@code ApEditVoucherInfo}'s "Existing Voucher No." column directly
 * (bypassing its own data source's MasterTestData/TestCaseID join, for the same reason).
 *
 * <p><b>Scope note:</b> no per-row/cell Sub-Ledger Information grid validation — only the grid's
 * headers, both already free from the shared base. The conditional TDS/Net Payable popup
 * ({@code APVoucherTdsAndNetPayable}) is out of scope for this pass — see the framework plan's AP
 * section. "View Mode"/{@code UiFieldState.READONLY} is likewise not exercised here, same reason
 * as GV/AR's own test classes.
 */
@ApiFlow(FinanceApiPattern.AP_VOUCHER)
public class ApVoucherUiValidation extends WebTest {

    @DataProvider(name = "ApVoucherUiAddData")
    public Object[][] addFormUiData() {
        MergedFinanceApVoucherData placeholder = MergedFinanceApVoucherData.builder()
                .subLedgerInformationList(List.of())
                .selectDocumentList(List.of())
                .paymentInformationList(List.of())
                .invocationIndex(1)
                .totalInvocations(1)
                .build();
        return new Object[][]{{placeholder}};
    }

    @DataProvider(name = "ApVoucherUiEditData")
    public Object[][] editFormUiData() {
        String source = ConfigFactory.getConfig().testDataSource();
        String existingVoucherNo = "DB".equalsIgnoreCase(source) ? resolveVoucherNoFromDb() : resolveVoucherNoFromExcel();
        Log.info("[ApVoucherUiValidation] Resolved existingVoucherNo='{}' (source={}) for Edit-form UI check",
                existingVoucherNo, source);
        MergedFinanceApVoucherData row = MergedFinanceApVoucherData.builder()
                .existingVoucherNo(existingVoucherNo)
                .subLedgerInformationList(List.of())
                .selectDocumentList(List.of())
                .paymentInformationList(List.of())
                .invocationIndex(1)
                .totalInvocations(1)
                .build();
        return new Object[][]{{row}};
    }

    /** Most recent NON-released {@code vchtype='P'} row — see
     *  {@code FinVchMainDao#findMostRecentByVoucherType}'s own Javadoc (a Released voucher blocks
     *  double-click-edit with an alert) and {@code GeneralVoucherUiValidation#resolveVoucherNoFromDb}'s
     *  identical rationale. */
    private static String resolveVoucherNoFromDb() {
        FinVchMain header = DatabaseManager.getJdbi().onDemand(FinVchMainDao.class)
                .findMostRecentByVoucherType(VoucherType.AP.code())
                .orElseThrow(() -> new SkipException(
                        "[ApVoucherUiValidation] No AP Voucher (vchtype='" + VoucherType.AP.code()
                        + "') found in the database — cannot resolve an existing voucher to open for "
                        + "the Edit-form UI-contract check."));
        return header.getSchemeDocno();
    }

    /** Reuses the SAME sheet/column {@code EditApVoucher} already depends on
     *  ({@code ApEditVoucherInfo}'s "Existing Voucher No." column) — takes whichever row's voucher
     *  number is already configured there, no MasterTestData/TestCaseID join needed. */
    private static String resolveVoucherNoFromExcel() {
        List<ApEditVoucherInfo> rows = use(XlsxReader.class)
                .withTarget(ApEditVoucherInfo.class)
                .withSource(ConfigFactory.getExcelDataConfig().apVoucherPath())
                .read().toList();
        String voucherNo = rows.isEmpty() ? null : rows.get(0).getExistingVoucherNo();
        if (voucherNo == null || voucherNo.isBlank()) {
            throw new SkipException(
                    "[ApVoucherUiValidation] No 'ApEditVoucherInfo' row with an 'Existing Voucher No.' "
                    + "found in the AP Voucher Excel sheet — cannot resolve an existing voucher to open "
                    + "for the Edit-form UI-contract check.");
        }
        return voucherNo;
    }

    @FrameworkAnnotation(value = TestAuthor.ARANI, module = MainModuleType.FINANCE, subModule = "FinanceApVoucher", operation = AppOperation.NONE)
    @TestCaseId("FIN-AP-UI-ADD")
    @Test(description = "UI-contract validation of the AP Voucher Add form",
            dataProvider = "ApVoucherUiAddData")
    public void validateAddFormUiContract(MergedFinanceApVoucherData data) {
        ActualResultsHolder.get().setVoucherType("AP");

        final HomePage[]           homeRef = new HomePage[1];
        final ApVoucherListPage[] listRef  = new ApVoucherListPage[1];
        final ApVoucherAdd[]      addRef   = new ApVoucherAdd[1];

        StepExecutor.step("Login to Application", () -> {
            UserCredential u = DriverManager.getCurrentUser();
            homeRef[0] = SessionManager.ensureLoggedIn(u);
            ReportLogger.business("Logged in as: " + u.username());
        });

        StepExecutor.step("Open Finance → AP Voucher List", () -> listRef[0] =
                NavigationUtils.navigateToFinanceSideMenu(
                        homeRef[0], MainMenuType.OPERATIONS, OperationsMenuType.APVOUCHER,
                        ApVoucherListPage.class));

        StepExecutor.step("Open Add Form", () -> addRef[0] = listRef[0].clickAdd());

        StepExecutor.step("Validate Add Form UI Contract", () ->
                addRef[0].validateUiContract(CONTRACT, EDITABLE, false));

        StepExecutor.step("Close Add Form (discard)", () -> {
            listRef[0] = addRef[0].close().clickYes(ApVoucherListPage::new);
            ReportLogger.business("Add form closed (discarded), returned to list");
        });
    }

    @FrameworkAnnotation(value = TestAuthor.ARANI, module = MainModuleType.FINANCE, subModule = "FinanceApVoucher", operation = AppOperation.NONE)
    @TestCaseId("FIN-AP-UI-EDIT")
    @Test(description = "UI-contract validation of the AP Voucher Edit form",
            dataProvider = "ApVoucherUiEditData")
    public void validateEditFormUiContract(MergedFinanceApVoucherData data) {
        ActualResultsHolder.get().setVoucherType("AP");
        ActualResultsHolder.get().setTestData(data);

        final HomePage[]           homeRef = new HomePage[1];
        final ApVoucherListPage[] listRef  = new ApVoucherListPage[1];
        final ApVoucherAdd[]      editRef  = new ApVoucherAdd[1];

        StepExecutor.step("Login to Application", () -> {
            UserCredential u = DriverManager.getCurrentUser();
            homeRef[0] = SessionManager.ensureLoggedIn(u);
            ReportLogger.business("Logged in as: " + u.username());
        });

        StepExecutor.step("Open Finance → AP Voucher List", () -> listRef[0] =
                NavigationUtils.navigateToFinanceSideMenu(
                        homeRef[0], MainMenuType.OPERATIONS, OperationsMenuType.APVOUCHER,
                        ApVoucherListPage.class));

        StepExecutor.step("Clear Filter and Refresh List", () ->
                listRef[0].clearFilterAndRefreshList().resetToFirstPageIfEnabled());

        StepExecutor.step("Open Existing Voucher for Edit", () -> {
            editRef[0] = listRef[0].doubleClickToEditFast(data.getExistingVoucherNo());
            editRef[0].verifyEditFormFullyLoaded(data.getExistingVoucherNo());
        });

        StepExecutor.step("Validate Edit Form UI Contract", () ->
                editRef[0].validateUiContract(CONTRACT, EDITABLE, true));

        StepExecutor.step("Close Edit Form (discard)", () -> {
            listRef[0] = editRef[0].close().clickYes(ApVoucherListPage::new);
            ReportLogger.business("Edit form closed (discarded), returned to list");
        });
    }
}
