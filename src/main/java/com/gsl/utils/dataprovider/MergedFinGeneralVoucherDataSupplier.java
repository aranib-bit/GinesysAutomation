package com.gsl.utils.dataprovider;

import com.aventstack.extentreports.ExtentTest;
import com.gsl.annotations.TestCaseId;
import com.gsl.config.ConfigFactory;
import com.gsl.enums.TestDataSourceMode;
import com.gsl.reports.ExtentReport;
import com.gsl.testdata.dto.MergedFinanceGeneralVoucherData;
import com.gsl.utils.Log;
import com.gsl.utils.dataprovider.source.TestDataSource;
import com.gsl.utils.dataprovider.source.finance.ApiGeneralVoucherDataSource;
import com.gsl.utils.dataprovider.source.finance.ApiGeneralVoucherDeleteDataSource;
import com.gsl.utils.dataprovider.source.finance.ApiGeneralVoucherEditDataSource;
import com.gsl.utils.dataprovider.source.finance.ApiGeneralVoucherRevertDataSource;
import com.gsl.utils.dataprovider.source.finance.DbGeneralVoucherDataSource;
import com.gsl.utils.dataprovider.source.finance.DbGeneralVoucherDeleteDataSource;
import com.gsl.utils.dataprovider.source.finance.DbGeneralVoucherEditDataSource;
import com.gsl.utils.dataprovider.source.finance.DbGeneralVoucherRevertDataSource;
import com.gsl.utils.dataprovider.source.finance.ExcelGeneralVoucherDataSource;
import com.gsl.utils.dataprovider.source.finance.ExcelGeneralVoucherEditDataSource;
import com.gsl.utils.dataprovider.source.finance.ExcelGeneralVoucherRevertDataSource;
import io.github.sskorol.core.DataSupplier;
import one.util.streamex.StreamEx;
import org.testng.ITestContext;

import java.lang.reflect.Method;
import java.util.List;

/**
 * DataSupplier router for General Voucher tests — mirrors
 * {@code MergedFinApVoucherDataSupplier}'s shape.
 * <ul>
 *   <li>{@code testdata.source=EXCEL} (default) → {@link ExcelGeneralVoucherDataSource} for
 *       Create, {@link ExcelGeneralVoucherRevertDataSource} for {@code revertGeneralVoucher}.</li>
 *   <li>{@code testdata.source=DB} → {@link DbGeneralVoucherDataSource} for Create,
 *       {@link DbGeneralVoucherRevertDataSource} for {@code revertGeneralVoucher} — same
 *       method-name switch shape {@code MergedFinArVoucherDataSupplier} uses on its DB branch for
 *       {@code deleteArVoucher}/{@code editArVoucher}.</li>
 * </ul>
 *
 * <h3>DB mode filtering</h3>
 * Two orthogonal filters are read from TestNG XML parameters (IntelliJ direct-run path)
 * or system properties (Maven Surefire path):
 * <ul>
 *   <li>{@code gv.group} — execution-group gate (MANDATORY / REGRESSION_PHASE1 / PHASE2 / PHASE3)</li>
 *   <li>{@code gv.tag}   — tag-set gate, comma-separated (EXPENSE, ASSET, TYPE2_SITE_ONLY, …)</li>
 * </ul>
 */
public class MergedFinGeneralVoucherDataSupplier {

    // runInParallel=false — see MergedFinApVoucherDataSupplier/MergedFinArVoucherDataSupplier's
    // identical setting: runInParallel=true spawns a short-lived per-method executor pool whose
    // threads are discarded once that method's rows finish; under the deferred-logout/
    // session-reuse model, the UserPool user + browser + DB connection are bound to the worker
    // THREAD between rows, so a discarded pool's threads die still holding those resources,
    // starving every other method's rows (confirmed live 2026-08-18: all 4 editGeneralVoucherHeader
    // rows launched simultaneously on a 4-thread pool and every one failed with
    // HikariPool "Connection is not available" once that pool's threads died holding connections
    // open for the run's full duration). Sequential rows on the long-lived <test>-block worker
    // thread is the correct model here too — parallelism still comes from
    // parallel="methods" thread-count, one thread = one user = one browser iterating rows.
    // Everything below — not just the loader.load() call — is wrapped in one try/catch. This
    // method runs reflectively (test-data-supplier invokes it via org.joor.Reflect, per that
    // library's ReflectionUtils.invokeDataSupplier), so ANY exception thrown from ANYWHERE in this
    // method — including the source/param resolution above the old try block, and including a bare
    // SkipException from loader.load() that used to be rethrown completely unlogged — gets wrapped
    // by joor into a bare, contextless org.joor.ReflectException: InvocationTargetException at the
    // TestNG layer, with duration=0 and NONE of this method's own logging having fired (confirmed
    // live, run 20260901_151519, FIN-GV-EDIT-DETAIL: the ExtentReport captured only that one-line
    // wrapper — zero stack trace, zero screenshot, no indication which data source or build-method
    // actually threw). Logging every path here — before rethrowing unchanged — is the only way to
    // make the *next* such failure diagnosable instead of a dead end.
    /**
     * Resolves the configured {@link TestDataSource} (EXCEL or DB, switched further by {@code
     * method}'s name for Revert/Edit/Delete vs. default-Create — see the class Javadoc's
     * method-to-source mapping), loads that source's scenario rows, stamps each row's {@code
     * TestCaseId}/{@code testcasename} from the method's own {@link TestCaseId} annotation when
     * missing or blank, and emits one {@code Object[]} (null credential + row) per scenario.
     *
     * <p>Every exception path — including one thrown before the try block would otherwise start,
     * since this whole method is reflectively invoked via {@code org.joor.Reflect} — is logged
     * (SLF4J) and also surfaced into the Extent HTML report via {@link
     * #reportDataSupplierFailure} before being converted to (or rethrown as) a {@link
     * org.testng.SkipException}, so a data-load failure is diagnosable instead of a bare, opaque
     * {@code ReflectException} wrapper.
     *
     * @param method  the TestNG-injected test method — its name selects the data source and
     *                supplies the {@link TestCaseId} annotation used for row stamping
     * @param context supplies the TestNG XML {@code gv.group}/{@code gv.tag}/{@code gv.edit.group}
     *                parameters
     */
    @DataSupplier(name = "FinanceGeneralVoucherData", flatMap = true, runInParallel = false)
    public StreamEx<Object[]> dataSupplier(Method method, ITestContext context) {
        String source = ConfigFactory.getConfig().testDataSource();
        TestDataSourceMode sourceMode = TestDataSourceMode.from(source);
        try {
            Log.info("[DataSupplier] testdata.source={}, method={}", source, method.getName());

            // Read gv.group and gv.tag from TestNG XML <parameter> — used as fallback when
            // the system property is absent (IntelliJ direct-run; Maven sets system props instead).
            String xmlGroupParam = context.getCurrentXmlTest().getParameter("gv.group");
            String xmlTagParam   = context.getCurrentXmlTest().getParameter("gv.tag");

            // gv.edit.group — Edit's own execution-group gate, deliberately distinct from gv.group
            // (Create's) since Edit's Mandatory/Regression membership differs — see
            // GeneralVoucherEditExecutionGroup's Javadoc.
            String xmlEditGroupParam = context.getCurrentXmlTest().getParameter("gv.edit.group");

            if (sourceMode == TestDataSourceMode.API && !isApiSupported(method.getName())) {
                throw new org.testng.SkipException("testdata.source=API is not implemented yet for '" + method.getName()
                        + "' (Create, Mixed, Revert, Delete and Edit are supported).");
            }
            TestDataSource<MergedFinanceGeneralVoucherData> loader =
                    sourceMode == TestDataSourceMode.API
                            ? switch (method.getName()) {
                                case "revertGeneralVoucher" -> new ApiGeneralVoucherRevertDataSource(xmlGroupParam, xmlTagParam);
                                case "deleteGeneralVoucher" -> new ApiGeneralVoucherDeleteDataSource();
                                case "editGeneralVoucherHeader", "editGeneralVoucherDetailLine",
                                     "editGeneralVoucherPaymentInfo" -> new ApiGeneralVoucherEditDataSource(xmlEditGroupParam);
                                default -> new ApiGeneralVoucherDataSource(xmlGroupParam, xmlTagParam);
                              }
                    : sourceMode == TestDataSourceMode.DB
                            ? switch (method.getName()) {
                                case "revertGeneralVoucher" -> new DbGeneralVoucherRevertDataSource(xmlGroupParam, xmlTagParam);
                                case "editGeneralVoucherHeader", "editGeneralVoucherDetailLine",
                                     "editGeneralVoucherPaymentInfo" ->
                                        new DbGeneralVoucherEditDataSource(xmlEditGroupParam);
                                case "deleteGeneralVoucher" -> new DbGeneralVoucherDeleteDataSource();
                                default -> new DbGeneralVoucherDataSource(xmlGroupParam, xmlTagParam);
                              }
                            : switch (method.getName()) {
                                case "revertGeneralVoucher" -> new ExcelGeneralVoucherRevertDataSource();
                                case "editGeneralVoucherHeader", "editGeneralVoucherDetailLine",
                                     "editGeneralVoucherPaymentInfo" ->
                                        new ExcelGeneralVoucherEditDataSource();
                                default -> new ExcelGeneralVoucherDataSource();
                              };

            List<MergedFinanceGeneralVoucherData> scenarios = loader.load(method.getName());

            // Point 1 (recurring fix — TestCase ID in the "Test Data — Header" table): `method`
            // is the ACTUAL TestNG-injected test method here, so its real @TestCaseId annotation
            // is directly available — the one genuine source of truth, already what the report's
            // own title banner is built from (TestListener.onTestStart uses the identical
            // String.format("%s-%02d", ...) shape). Every prior attempt at this field guessed at
            // an internal placeholder string (a combination-matrix token, or a literal
            // "MIXED_TYPES") instead of the real id — stamping it here, once, right after load(),
            // fixes it for every scenario this data source can produce (combination-matrix rows
            // AND the MIXED_TYPES row alike) without touching TestDataSource's shared interface
            // or any individual loader implementation.
            //
            // Item 4 (Round 12): the above claim ("fixes it for every scenario") was only ever
            // true for DbGeneralVoucherDataSource (Create) — confirmed live (blank "TestCase ID"/
            // "TestCase Name" in "Test Data — Header" for both editGeneralVoucherHeader and
            // revertGeneralVoucher): DbGeneralVoucherEditDataSource/DbGeneralVoucherRevertDataSource
            // never construct/set masterTestData on any row at all, so the old
            // `if (row.getMasterTestData() != null)` guard silently skipped every one of their
            // rows. Now builds a MasterTestData when missing instead of skipping — testcasename
            // reuses GeneralVoucherDescriptionBuilder.build(row), the SAME sentence already proven
            // correct for the title banner (same idiom as Round 11's MIXED_TYPES fix), so the two
            // can never drift apart. No changes needed in the individual Edit/Revert/Delete data
            // source classes themselves.
            TestCaseId tcIdAnnotation = method.getAnnotation(TestCaseId.class);
            if (tcIdAnnotation != null) {
                for (MergedFinanceGeneralVoucherData row : scenarios) {
                    String stampedId = String.format("%s-%02d", tcIdAnnotation.value(), row.getInvocationIndex());
                    if (row.getMasterTestData() == null) {
                        row.setMasterTestData(new com.gsl.testdata.MasterTestData(
                                com.gsl.reports.GeneralVoucherDescriptionBuilder.build(row),
                                null, null, null, stampedId, row.getInvocationIndex()));
                    } else {
                        row.getMasterTestData().setTestcaseid(stampedId);
                        String existingName = row.getMasterTestData().getTestcasename();
                        if (existingName == null || existingName.isBlank()) {
                            row.getMasterTestData().setTestcasename(
                                    com.gsl.reports.GeneralVoucherDescriptionBuilder.build(row));
                        }
                    }
                }
            }

            Log.info("[DataSupplier] Emitting {} General Voucher scenario row(s) for '{}' — "
                    + "users assigned by pool at runtime.", scenarios.size(), method.getName());

            // null credential → WebTest.setUp() acquires from UserPool (blocking)
            return StreamEx.of(scenarios).map(data -> new Object[]{null, data});

        } catch (org.testng.SkipException se) {
            String msg = String.format("[DataSupplier] SKIPPED '%s' (source=%s) — %s",
                    method.getName(), source, se.getMessage());
            Log.warn(msg);
            reportDataSupplierFailure(method.getName(), msg);
            throw se;
        } catch (Throwable e) {
            Throwable root = e;
            while (root.getCause() != null) root = root.getCause();

            String msg = String.format(
                    "[DataSupplier] SKIPPED '%s' — could not load General Voucher test data (source=%s).%n" +
                    "  Immediate cause : %s%n" +
                    "  Root cause      : %s%n" +
                    "  Action          : Verify manually — ping the host, check DB service status,%n" +
                    "                    or switch testdata.source=EXCEL to run without DB.",
                    method.getName(), source, e.getMessage(), root.getMessage());
            Log.error(msg);
            reportDataSupplierFailure(method.getName(), msg);
            throw new org.testng.SkipException(msg);
        }
    }

    /** Methods that already have an API data source. */
    private static boolean isApiSupported(String methodName) {
        return "createGeneralVoucher".equals(methodName) || "createGeneralVoucherMixedTypes".equals(methodName)
                || "revertGeneralVoucher".equals(methodName) || "deleteGeneralVoucher".equals(methodName)
                || "editGeneralVoucherHeader".equals(methodName) || "editGeneralVoucherDetailLine".equals(methodName)
                || "editGeneralVoucherPaymentInfo".equals(methodName);
    }

    /**
     * Gets a data-provider-level failure INTO the HTML report itself, not just SLF4J logs.
     * This method runs before TestNG ever calls {@code onTestStart} for the invocation being
     * supplied, so {@code ExtentManager.getExtentTest()} is null here — every {@code ExtentLogger}
     * method is a silent no-op with no active test node (confirmed live, run 20260901_185011,
     * FIN-GV-EDIT-DETAIL: the Round-1 {@code Log.error} fix logged correctly to SLF4J, but the
     * ExtentReport HTML still showed only the bare {@code org.joor.ReflectException} wrapper with
     * zero further detail). Creates its own standalone top-level test node instead — the same
     * {@code ExtentReports.createTest(...)} idiom {@code PreSuiteHealthCheck.checkUrl} already
     * uses for infrastructure failures that similarly happen outside the normal per-test flow.
     * Best-effort and defensive: must never let a reporting problem mask the real
     * {@code SkipException}/failure being thrown by the caller.
     */
    private static void reportDataSupplierFailure(String methodName, String message) {
        try {
            var extent = ExtentReport.getExtentReports();
            if (extent == null) return;
            ExtentTest node = extent.createTest("[DataProvider Failure] " + methodName);
            node.skip(message);
        } catch (Exception reportingFailure) {
            Log.warn("[DataSupplier] Could not attach failure to report: {}", reportingFailure.getMessage());
        }
    }
}
