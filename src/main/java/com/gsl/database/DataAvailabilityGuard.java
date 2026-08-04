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
 * <p>Takes a {@code Supplier<DocumentSelectionResult>} rather than a {@code SelectorRequest} so
 * this same guard wraps both AR's and AP's shortfall methods without either one changing its
 * own retry/reservation logic at all.
 */
public final class DataAvailabilityGuard {

    private DataAvailabilityGuard() {}

    public static DocumentSelectionResult selectOrBackfill(
            Supplier<DocumentSelectionResult> selectionAttempt,
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
                DocumentSelectionResult retryResult = selectionAttempt.get();
                ReportLogger.dataBackfill("[DataAvailabilityGuard] Retry after backfill — resolved, {} SL(s) found",
                        retryResult.getSelectedSls().size());
                return retryResult;
            } finally {
                ExtentManager.setExtentTest(previousTest);
            }
        }
    }
}
