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
import com.gsl.enums.Finance.Operations.GeneralVoucher.GlLineType;
import com.gsl.enums.GlAccountType;
import com.gsl.testdata.UserCredential;
import com.gsl.testdata.dto.MergedFinanceGeneralVoucherData;
import com.gsl.utils.Log;
import com.gsl.utils.NavigationUtils;
import com.gsl.utils.dataprovider.fixture.GeneralVoucherFixtureFactory;
import com.gsl.utils.dataprovider.fixture.finance.GeneralVoucherFixtureOptions;
import com.gsl.utils.dataprovider.fixture.finance.GeneralVoucherPaymentVariant;
import com.gsl.utils.dataprovider.fixture.finance.GeneralVoucherScenarioType;
import com.gsl.utils.dataprovider.source.finance.DbGeneralVoucherDataSource;
import com.tests.WebTest;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * General Voucher — Negative &amp; Boundary scenarios.
 *
 * <p>Scoped to the 5 scenarios the plan calls out (§4, plus Round 21's Cash-post-dated and
 * Round 16's cross-OU-site additions): site-allocation Pending Amount != 0, Type3 row missing
 * Sub Ledger, a Type2 row whose Site is never allocated, a Cash-type ledger with a post-dated
 * Document Date, and a GL-linked/user-assigned site belonging to a different Organizational Unit
 * than the Owner Site (must not be selectable in Allocate Sites). Each scenario's data is
 * resolved live via {@code DbGeneralVoucherDataSource.loadFreshForScenario}
 * (see {@link #negativeBoundaryData} below) and built by
 * {@link com.gsl.utils.dataprovider.fixture.finance.GeneralVoucherTemplateLoader}'s
 * {@code negXxxTemplate} methods; {@code GeneralVoucherPredicateFactory} supplies only the
 * shared UI-interaction/assertion behavior.
 *
 * <p>Dispatches via {@link com.gsl.utils.dataprovider.fixture.finance.GeneralVoucherScenarioType#execute}
 * like every other domain's negative-boundary test — no more raw-tuple {@code @DataProvider}
 * carrying the behavior alongside the data (see the {@code VoucherScenarioType} unification;
 * this was the exact bypass that interface exists to make impossible).
 *
 * <p>Unlike {@code CreateApVoucher.createApVoucherNegativeBoundary}, this keeps its own
 * dedicated {@code @DataProvider} rather than routing through
 * {@code MergedFinGeneralVoucherDataSupplier} — that supplier exists to toggle Excel/DB sourcing
 * and apply {@code gv.group}/{@code gv.tag} filters, and to resolve one shared {@link
 * com.gsl.utils.dataprovider.fixture.LiveGvDbRefs} per combination, neither of which fits these
 * 5 scenarios (each needs its own differently-shaped refs — Type2 vs Type3, Bank vs Cash — see
 * {@link #negativeBoundaryData}). It also passes no explicit {@code UserCredential} parameter —
 * {@code WebTest.setUp()} falls back to its default SessionPool-borrow branch when none appears
 * in the test's actual parameters (confirmed via {@code WebTest.setUp}'s {@code
 * extractCredential} logic), so this still gets a pooled, logged-in session exactly like the
 * DataSupplier-driven positive test does.
 */
@ApiFlow(FinanceApiPattern.GENERAL_VOUCHER)
public class CreateGeneralVoucherNegativeBoundary extends WebTest {

    @DataProvider(name = "GeneralVoucherNegativeBoundaryData")
    public Object[][] negativeBoundaryData() {
        // Each NEG_* scenario resolves its GL/Site/Sub-Ledger/Cash-Bank ledger live via the same
        // DbGeneralVoucherDataSource pipeline the positive combination matrix uses — no more
        // hardcoded literal GL/Site names. All 4 use a live EXPENSE GL (matching the positive
        // matrix's own Mandatory-tier Expense/Type2/Debit baseline) since the negative assertion
        // is about Site/Sub-Ledger/Cash-payment validation, not accounting-type correctness.
        Map<GeneralVoucherScenarioType, GeneralVoucherFixtureOptions> scenarios = new LinkedHashMap<>();
        scenarios.put(GeneralVoucherScenarioType.NEG_SITE_ALLOCATION_PENDING,
                GeneralVoucherFixtureOptions.of(GlAccountType.EXPENSE, GlLineType.TYPE2_SITE_ONLY,
                        true, false, GeneralVoucherPaymentVariant.NONE));
        scenarios.put(GeneralVoucherScenarioType.NEG_SITE_NOT_ALLOCATED,
                GeneralVoucherFixtureOptions.of(GlAccountType.EXPENSE, GlLineType.TYPE2_SITE_ONLY,
                        true, false, GeneralVoucherPaymentVariant.NONE));
        // requireAmbiguousSubLedger=true — this scenario needs a Type3 GL with MORE THAN ONE
        // eligible Sub Ledger (see GeneralVoucherTemplateLoader.negSubledgerRequiredTemplate's Javadoc).
        scenarios.put(GeneralVoucherScenarioType.NEG_SUBLEDGER_REQUIRED,
                GeneralVoucherFixtureOptions.of(GlAccountType.EXPENSE, GlLineType.TYPE3_SITE_AND_SUBLEDGER,
                        true, false, GeneralVoucherPaymentVariant.NONE, true));
        // CASH (not NONE/Bank) — this scenario needs a live Cash-type Cash/Bank ledger.
        scenarios.put(GeneralVoucherScenarioType.NEG_CASH_POST_DATED,
                GeneralVoucherFixtureOptions.of(GlAccountType.EXPENSE, GlLineType.TYPE2_SITE_ONLY,
                        true, false, GeneralVoucherPaymentVariant.CASH));
        // CASH + Type2, like NEG_CASH_POST_DATED (a proven-resolvable shape). EXHAUSTED makes the data
        // source hand back a document scheme with no numbers left, provisioning/filling a dedicated
        // test scheme first when none exists (see ExhaustedDocSchemeBackfill) — skipped, not failed,
        // in environments where that is not allowed.
        scenarios.put(GeneralVoucherScenarioType.NEG_DOC_SCHEME_EXHAUSTED,
                GeneralVoucherFixtureOptions.of(GlAccountType.EXPENSE, GlLineType.TYPE2_SITE_ONLY,
                        true, false, GeneralVoucherPaymentVariant.CASH)
                        .toBuilder()
                        .docSchemeMode(com.gsl.utils.dataprovider.source.finance.common.DocSchemeMode.EXHAUSTED)
                        .build());
        // Round 16 — needs a GL with at least one fingl_site-linked, user-assigned site whose OWN
        // admou_code differs from the resolved Owner Site's (see ofCrossOuSite's Javadoc and
        // DbGeneralVoucherDataSource#resolveCrossOuSiteForNegativeTest).
        scenarios.put(GeneralVoucherScenarioType.NEG_SITE_ALLOCATION_CROSS_OU,
                GeneralVoucherFixtureOptions.ofCrossOuSite(GlAccountType.EXPENSE, GlLineType.TYPE2_SITE_ONLY,
                        true, false, GeneralVoucherPaymentVariant.NONE));

        return scenarios.entrySet().stream()
                .map(e -> DbGeneralVoucherDataSource.loadFreshForScenario(
                        GeneralVoucherFixtureFactory.buildNegativeBoundaryPlaceholder(e.getKey(), e.getValue()),
                        "createGeneralVoucherNegativeBoundary"))
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
        // Business log + "Navigated: ..." summary are now recorded inside
        // NavigationUtils.navigateToFinanceSideMenu itself.
        StepExecutor.step("Open Finance → General Voucher List", () -> listRef[0] =
                NavigationUtils.navigateToFinanceSideMenu(
                        homeRef[0],
                        MainMenuType.OPERATIONS,
                        OperationsMenuType.CATEGORY,
                        GeneralVoucherListPage.class));

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
