package com.gsl.utils.dataprovider;

import com.gsl.config.ConfigFactory;
import com.gsl.testdata.dto.MergedFinanceGeneralVoucherData;
import com.gsl.utils.Log;
import com.gsl.utils.dataprovider.source.TestDataSource;
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
    @DataSupplier(name = "FinanceGeneralVoucherData", flatMap = true, runInParallel = false)
    public StreamEx<Object[]> dataSupplier(Method method, ITestContext context) {
        String source = ConfigFactory.getConfig().testDataSource();
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

            TestDataSource<MergedFinanceGeneralVoucherData> loader =
                    "DB".equalsIgnoreCase(source)
                            ? switch (method.getName()) {
                                case "revertGeneralVoucher" -> new DbGeneralVoucherRevertDataSource(xmlGroupParam, xmlTagParam);
                                case "editGeneralVoucherHeader", "editGeneralVoucherDetailLine" ->
                                        new DbGeneralVoucherEditDataSource(xmlEditGroupParam);
                                case "deleteGeneralVoucher" -> new DbGeneralVoucherDeleteDataSource();
                                default -> new DbGeneralVoucherDataSource(xmlGroupParam, xmlTagParam);
                              }
                            : switch (method.getName()) {
                                case "revertGeneralVoucher" -> new ExcelGeneralVoucherRevertDataSource();
                                case "editGeneralVoucherHeader", "editGeneralVoucherDetailLine" ->
                                        new ExcelGeneralVoucherEditDataSource();
                                default -> new ExcelGeneralVoucherDataSource();
                              };

            List<MergedFinanceGeneralVoucherData> scenarios = loader.load(method.getName());

            Log.info("[DataSupplier] Emitting {} General Voucher scenario row(s) for '{}' — "
                    + "users assigned by pool at runtime.", scenarios.size(), method.getName());

            // null credential → WebTest.setUp() acquires from UserPool (blocking)
            return StreamEx.of(scenarios).map(data -> new Object[]{null, data});

        } catch (org.testng.SkipException se) {
            Log.warn("[DataSupplier] SKIPPED '{}' (source={}) — {}", method.getName(), source, se.getMessage());
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
            throw new org.testng.SkipException(msg);
        }
    }
}
