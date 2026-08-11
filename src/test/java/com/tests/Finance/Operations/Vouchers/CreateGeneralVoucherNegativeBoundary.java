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
import com.gsl.utils.dataprovider.fixture.finance.GeneralVoucherPredicateFactory;
import com.tests.WebTest;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.function.BiPredicate;
import java.util.function.Supplier;

/**
 * General Voucher — Negative &amp; Boundary scenarios.
 *
 * <p>Scoped to the 3 candidate scenarios the plan calls out (§4):
 * site-allocation Pending Amount != 0, Type3 row missing Sub Ledger, and a
 * Type2 row whose Site is never allocated. See
 * {@link GeneralVoucherPredicateFactory} for the data + shared behavior.
 *
 * <p>Unlike {@code CreateApVoucher.createApVoucherNegativeBoundary}, this uses
 * a plain TestNG {@code @DataProvider} rather than the DataSupplier/UserPool
 * infrastructure — {@code WebTest.setUp()} falls back to its default
 * SessionPool-borrow branch when no {@code UserCredential} appears in the
 * test's actual parameters (confirmed via {@code WebTest.setUp}'s
 * {@code extractCredential} logic), so this still gets a pooled, logged-in
 * session exactly like the DataSupplier-driven positive test does.
 */
@ApiFlow(FinanceApiPattern.GENERAL_VOUCHER)
public class CreateGeneralVoucherNegativeBoundary extends WebTest {

    /**
     * Third column picks the shared behavior each scenario actually needs — not every negative
     * scenario validates the same way. {@link GeneralVoucherPredicateFactory#subLedgerRequired()}
     * is blocked by the app at the Site-button click, before Save is ever reached (confirmed live,
     * run 20260807_171711), so it needs {@code NEG_SUBLEDGER_REQUIRED_BEHAVIOR} — the other two
     * genuinely fail at Save and use the shared {@code NEGATIVE_FORM_VALIDATION_BEHAVIOR}. Same
     * per-scenario-behavior idiom {@code ApVoucherPredicateFactory}/{@code ArVoucherPredicateFactory}
     * use (their own {@code *ScenarioType} enum maps a scenario to one of several behaviors);
     * this DataProvider just makes the mapping explicit per row instead of via an enum, since GV's
     * negative suite is 3 rows, not the dozens AP/AR's scaffolding was built for.
     */
    @DataProvider(name = "GeneralVoucherNegativeBoundaryData")
    public Object[][] negativeBoundaryData() {
        return new Object[][]{
                {"Site allocation Pending Amount != 0", GeneralVoucherPredicateFactory.siteAllocationPending(),
                        GeneralVoucherPredicateFactory.NEGATIVE_FORM_VALIDATION_BEHAVIOR},
                {"Type3 row missing Sub Ledger",        GeneralVoucherPredicateFactory.subLedgerRequired(),
                        GeneralVoucherPredicateFactory.NEG_SUBLEDGER_REQUIRED_BEHAVIOR},
                {"Type2 row — Site never allocated",    GeneralVoucherPredicateFactory.siteNotAllocated(),
                        GeneralVoucherPredicateFactory.NEGATIVE_FORM_VALIDATION_BEHAVIOR},
        };
    }

    @FrameworkAnnotation(value = TestAuthor.ARANI, module = MainModuleType.FINANCE, subModule = "FinanceGenVoucher", operation = AppOperation.ADD)
    @TestCaseId("FIN-GV-ADD-NEG")
    @Test(description = "Create General Voucher – Negative & Boundary",
            dataProvider = "GeneralVoucherNegativeBoundaryData")
    public void createGeneralVoucherNegativeBoundary(String scenarioName, MergedFinanceGeneralVoucherData data,
            Supplier<BiPredicate<MergedFinanceGeneralVoucherData, GeneralVoucherAdd>> behavior) {

        Log.info("Create General Voucher (Negative & Boundary) — scenario: {}", scenarioName);

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
            ReportLogger.business("Scenario: " + scenarioName + " | expect error: "
                    + data.getExpectedValidation().expectedMessage());
        });

        // ── Delegate fill/save/verify/logout to whichever behavior this scenario needs ────────────
        boolean ok = behavior.get().test(data, addRef[0]);
        Assert.assertTrue(ok, "Negative-boundary scenario did not complete successfully: " + scenarioName);
    }
}
