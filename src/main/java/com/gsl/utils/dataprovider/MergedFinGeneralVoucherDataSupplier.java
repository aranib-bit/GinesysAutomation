package com.gsl.utils.dataprovider;

import com.gsl.config.ConfigFactory;
import com.gsl.testdata.dto.MergedFinanceGeneralVoucherData;
import com.gsl.utils.Log;
import com.gsl.utils.dataprovider.source.TestDataSource;
import com.gsl.utils.dataprovider.source.finance.DbGeneralVoucherDataSource;
import com.gsl.utils.dataprovider.source.finance.ExcelGeneralVoucherDataSource;
import io.github.sskorol.core.DataSupplier;
import one.util.streamex.StreamEx;
import org.testng.ITestContext;

import java.lang.reflect.Method;
import java.util.List;

/**
 * DataSupplier router for General Voucher tests — mirrors
 * {@code MergedFinApVoucherDataSupplier}'s shape.
 * <ul>
 *   <li>{@code testdata.source=EXCEL} (default) → {@link ExcelGeneralVoucherDataSource}</li>
 *   <li>{@code testdata.source=DB}              → {@link DbGeneralVoucherDataSource}</li>
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

    @DataSupplier(name = "FinanceGeneralVoucherData", flatMap = true, runInParallel = true)
    public StreamEx<Object[]> dataSupplier(Method method, ITestContext context) {
        String source = ConfigFactory.getConfig().testDataSource();
        Log.info("[DataSupplier] testdata.source={}, method={}", source, method.getName());

        // Read gv.group and gv.tag from TestNG XML <parameter> — used as fallback when
        // the system property is absent (IntelliJ direct-run; Maven sets system props instead).
        String xmlGroupParam = context.getCurrentXmlTest().getParameter("gv.group");
        String xmlTagParam   = context.getCurrentXmlTest().getParameter("gv.tag");

        TestDataSource<MergedFinanceGeneralVoucherData> loader =
                "DB".equalsIgnoreCase(source)
                        ? new DbGeneralVoucherDataSource(xmlGroupParam, xmlTagParam)
                        : new ExcelGeneralVoucherDataSource();

        try {
            List<MergedFinanceGeneralVoucherData> scenarios = loader.load(method.getName());

            Log.info("[DataSupplier] Emitting {} General Voucher scenario row(s) for '{}' — "
                    + "users assigned by pool at runtime.", scenarios.size(), method.getName());

            // null credential → WebTest.setUp() acquires from UserPool (blocking)
            return StreamEx.of(scenarios).map(data -> new Object[]{null, data});

        } catch (org.testng.SkipException se) {
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
