package com.tests.Finance.Operations.Vouchers;

import com.gsl.annotations.ApiFlow;
import com.gsl.annotations.FrameworkAnnotation;
import com.gsl.annotations.TestCaseId;
import com.gsl.driver.DriverManager;
import com.gsl.driver.SessionManager;
import com.gsl.enums.AppOperation;
import com.gsl.enums.TestAuthor;
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
import com.gsl.testdata.UserCredential;
import com.gsl.testdata.dto.MergedFinanceGeneralVoucherData;
import com.gsl.utils.Log;
import com.gsl.utils.NavigationUtils;
import com.gsl.utils.dataprovider.fixture.GeneralVoucherFixtureFactory;
import com.tests.WebTest;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * General Voucher — Negative &amp; Boundary scenarios.
 *
 * <p>Scoped to the 3 candidate scenarios the plan calls out (§4):
 * site-allocation Pending Amount != 0, Type3 row missing Sub Ledger, and a
 * Type2 row whose Site is never allocated. See
 * {@code GeneralVoucherPredicateFactory} for the data + shared behavior.
 *
 * <p>Dispatches via {@link com.gsl.utils.dataprovider.fixture.finance.GeneralVoucherScenarioType#execute}
 * like every other domain's negative-boundary test — no more raw-tuple {@code @DataProvider}
 * carrying the behavior alongside the data (see the {@code VoucherScenarioType} unification;
 * this was the exact bypass that interface exists to make impossible).
 *
 * <p>Unlike {@code CreateApVoucher.createApVoucherNegativeBoundary}, this keeps its own
 * dedicated {@code @DataProvider} rather than routing through
 * {@code MergedFinGeneralVoucherDataSupplier} — that supplier exists to toggle Excel/DB sourcing
 * and apply {@code gv.group}/{@code gv.tag} filters, none of which apply to these 3 hand-built
 * Java fixtures. It also passes no explicit {@code UserCredential} parameter — {@code
 * WebTest.setUp()} falls back to its default SessionPool-borrow branch when none appears in the
 * test's actual parameters (confirmed via {@code WebTest.setUp}'s {@code extractCredential}
 * logic), so this still gets a pooled, logged-in session exactly like the DataSupplier-driven
 * positive test does.
 */
@ApiFlow(FinanceApiPattern.GENERAL_VOUCHER)
public class CreateGeneralVoucherNegativeBoundary extends WebTest {

    @DataProvider(name = "GeneralVoucherNegativeBoundaryData")
    public Object[][] negativeBoundaryData() {
        // refs=null is safe — every NEG_* scenario's templateLoader ignores its refs parameter
        // (its data is hand-built in Java, not resolved from live DB refs). See
        // GeneralVoucherFixtureFactory#generate's javadoc.
        return new GeneralVoucherFixtureFactory()
                .generate("createGeneralVoucherNegativeBoundary", null)
                .stream()
                .map(data -> new Object[]{data})
                .toArray(Object[][]::new);
    }

    @FrameworkAnnotation(value = TestAuthor.ARANI, module = MainModuleType.FINANCE, subModule = "FinanceGenVoucher", operation = AppOperation.ADD)
    @TestCaseId("FIN-GV-ADD-NEG")
    @Test(description = "Create General Voucher – Negative & Boundary",
            dataProvider = "GeneralVoucherNegativeBoundaryData")
    public void createGeneralVoucherNegativeBoundary(MergedFinanceGeneralVoucherData data) {

        Log.info("Create General Voucher (Negative & Boundary) — scenario: {}", data.getScenarioType());

        ActualResultsHolder.get().setVoucherType("GV");
        ActualResultsHolder.get().setNegativeScenario(true);
        ActualResultsHolder.get().setTestData(data);

        final HomePage[]              homeRef = new HomePage[1];
        final GeneralVoucherListPage[] listRef = new GeneralVoucherListPage[1];
        final GeneralVoucherAdd[]      addRef  = new GeneralVoucherAdd[1];

        // ── Step 1: Login ─────────────────────────────────────────────────────────
        StepExecutor.step("Login to Application", () -> {
            UserCredential u = DriverManager.getCurrentUser();
            homeRef[0] = SessionManager.ensureLoggedIn(u);
            ReportLogger.business("Logged in as: " + u.username());
        });

        // ── Step 2: Navigate ──────────────────────────────────────────────────────
        StepExecutor.step("Open Finance → General Voucher List", () -> {
            listRef[0] = NavigationUtils.navigateToFinanceSideMenu(
                    homeRef[0],
                    MainMenuType.OPERATIONS,
                    OperationsMenuType.CATEGORY,
                    GeneralVoucherListPage.class);
            ReportLogger.business("General Voucher list page opened");
        });

        // ── Step 3: Open Add form ─────────────────────────────────────────────────
        StepExecutor.step("Open Add Form", () -> {
            addRef[0] = listRef[0].clickAdd();
            ReportLogger.business("Scenario: " + data.getScenarioType() + " | expect error: "
                    + data.getExpectedValidation().expectedMessage());
        });

        // ── Delegate fill/save/verify/logout to the scenario behaviour ────────────
        boolean ok = data.getScenarioType().execute(data, addRef[0]);
        Assert.assertTrue(ok, "Negative-boundary scenario did not complete successfully: " + data.getScenarioType());
    }
}
