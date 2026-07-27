package com.gsl.utils.dataprovider;

import com.gsl.config.ConfigFactory;
import com.gsl.testdata.Finance.Setup.AccountClass.AccountClassLifecycleRow;
import com.gsl.utils.Log;
import io.github.sskorol.core.DataSupplier;
import io.github.sskorol.data.XlsxReader;
import one.util.streamex.StreamEx;

import java.util.List;

import static io.github.sskorol.data.TestDataReader.use;

/**
 * DataSupplier for Account Class naming-convention scenarios — Excel-only (no DB-sourcing
 * counterpart like AP/AR Voucher's, since these are hand-crafted naming edge cases, not
 * live transactional data to source from a database).
 *
 * <p><b>Parallelism model — "run it in parallel like AP Voucher":</b> unlike
 * {@code MergedAccountGroupLifecycleDataSupplier} ({@code flatMap=false}, one invocation loops
 * every row internally), this uses {@code flatMap=true} like {@code MergedFinApVoucherDataSupplier}
 * — each Excel row becomes its OWN independent parallel test invocation.
 *
 * <p>{@code runInParallel=true} is SAFE here for the exact same reason it's safe for AP Voucher:
 * sessions live in {@code SessionPool}, not bound to the short-lived per-method thread pool
 * TestNG spins up for {@code runInParallel=true} — each row borrows a session in
 * {@code WebTest.setUp()} and returns it in {@code tearDown()}, so a dying row-thread holds
 * nothing between rows, and Account Class rows interleave with AP Voucher rows across the same
 * pooled sessions when both classes run in one {@code <test>} block.
 */
public class MergedAccountClassLifecycleDataSupplier {

    @DataSupplier(name = "AccountClassLifecycleData", flatMap = true, runInParallel = true)
    public StreamEx<Object[]> dataSupplier() {
        // System property override (set by the accountclass-single Maven profile) lets an ad hoc
        // single-row verification run point at a different Excel file without touching the shared
        // ExcelDataConfig — Owner 1.0.12 (pinned in pom.xml) doesn't reliably support a
        // "system:properties" @Config.Sources entry, so this is done explicitly instead.
        String path = System.getProperty("finAccountClassLifecyclePath",
                ConfigFactory.getExcelDataConfig().finAccountClassLifecyclePath());
        List<AccountClassLifecycleRow> rows = use(XlsxReader.class)
                .withTarget(AccountClassLifecycleRow.class)
                .withSource(path)
                .read().toList();

        Log.info("[MergedAccountClassLifecycleDataSupplier] Loaded {} Account Class row(s).", rows.size());

        // null credential -> WebTest.setUp() borrows a session from SessionPool (blocking)
        return StreamEx.of(rows).map(row -> new Object[]{null, row});
    }
}
