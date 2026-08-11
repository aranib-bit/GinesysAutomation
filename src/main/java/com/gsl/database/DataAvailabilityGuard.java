package com.gsl.database;

import com.aventstack.extentreports.ExtentTest;
import com.gsl.api.bulk.K6ProcessBulkDataProvisioner;
import com.gsl.api.core.PublicApiAvailabilityChecker;
import com.gsl.config.ConfigFactory;
import com.gsl.enums.ApiPlatform;
import com.gsl.reports.ExtentManager;
import com.gsl.reports.ExtentReport;
import com.gsl.reports.ReportLogger;
import com.gsl.reports.StepExecutor;
import org.testng.SkipException;

import java.util.function.Supplier;

/**
 * Wraps any shortfall-throwing document selector (AR's shared {@code DocumentSelector.selectAtomic}
 * or AP's private inline {@code selectApDocumentsAtomic}) with a data-availability backfill:
 * on a genuine shortfall (SkipException), creates what's needed via WEBAPI/PUBLICAPI (small
 * quantity) or k6 (bulk quantity), then retries once. If still short after backfill, the
 * shortfall is real and the original SkipException propagates.
 *
 * <p>Takes a {@code Supplier<T>} rather than a {@code SelectorRequest} so this same guard wraps
 * any shortfall-throwing lookup without the caller changing its own retry/reservation logic at
 * all — originally AR's/AP's own {@code Supplier<DocumentSelectionResult>} document selectors,
 * now generalized so General Voucher's master-data lookups (a missing {@code fingl}/{@code
 * finsl_gl} row, {@code Supplier<List<FinGl>>}/similar) reuse the exact same retry/report
 * wrapping instead of a second parallel mechanism.
 */
public final class DataAvailabilityGuard {

    private DataAvailabilityGuard() {}

    public static <T> T selectOrBackfill(
            Supplier<T> selectionAttempt,
            int neededQuantity,
            BackfillStrategy strategy) {
        try {
            return selectionAttempt.get();
        } catch (SkipException shortfall) {
            // Most callers reach here from a "lazy DB load" at the very top of the @Test method
            // body (see e.g. CreateApVoucher.java/CreateArVoucher.java), which runs AFTER
            // TestListener.onTestStart already created that test's ExtentTest node — so backfill
            // nests as a step group under the real, already-active test (appearing as its first
            // few steps, exactly where the lazy load happens in the method body), keeping it part
            // of that one test instead of spawning a separate entry in the report's Tests list.
            // Only if no test is active yet (a shortfall discovered during eager @DataProvider
            // resolution, before any test's node exists) does backfill fall back to its own
            // dedicated top-level entry, since there's nowhere else to attach it.
            ExtentTest previousTest = ExtentManager.getExtentTest();
            String recordWord = neededQuantity == 1 ? "record" : "records";
            String label = "Data BackFill — " + strategy.getClass().getSimpleName()
                    + " (" + neededQuantity + " " + recordWord + ")";
            if (previousTest != null) {
                ExtentManager.setExtentTest(StepExecutor.group(label));
            } else {
                ExtentReport.createTest(label);
            }
            ExtentManager.getExtentTest().assignCategory("DATA_BACKFILL");
            try {
                ReportLogger.dataBackfillWarn("[DataAvailabilityGuard] Shortfall — {} record(s) needed, none found in DB", neededQuantity);

                ApiPlatform choice = DataSourceRouter.decide(neededQuantity);
                ReportLogger.dataBackfill("[DataAvailabilityGuard] Source chosen: {} (order={}, publicApiAvailable={})",
                        choice, java.util.Arrays.toString(ConfigFactory.getApiConfig().smallQtyPreferredOrder()),
                        PublicApiAvailabilityChecker.isAvailable());

                ReportLogger.dataBackfill("[DataAvailabilityGuard] Creating {} record(s) via {} using {}",
                        neededQuantity, choice, strategy.getClass().getSimpleName());

                if (choice == ApiPlatform.K6BULK) {
                    new K6ProcessBulkDataProvisioner().provisionAndWait(strategy.k6ScriptKey(), neededQuantity);
                } else {
                    strategy.backfill(choice, neededQuantity);
                }

                // One retry after backfill. If STILL short, the shortfall is real — let it skip.
                T retryResult = selectionAttempt.get();
                ReportLogger.dataBackfill("[DataAvailabilityGuard] Retry after backfill — resolved");
                return retryResult;
            } finally {
                ExtentManager.setExtentTest(previousTest);
            }
        }
    }
}
