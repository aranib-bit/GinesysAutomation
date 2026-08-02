package com.gsl.database;

import com.gsl.api.bulk.K6ProcessBulkDataProvisioner;
import com.gsl.api.core.PublicApiAvailabilityChecker;
import com.gsl.config.ConfigFactory;
import com.gsl.enums.ApiPlatform;
import com.gsl.reports.ReportLogger;
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
        }
    }
}
