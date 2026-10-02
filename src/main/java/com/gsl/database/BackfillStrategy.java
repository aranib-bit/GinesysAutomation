package com.gsl.database;

import com.gsl.enums.ApiPlatform;

/**
 * One implementation per document/master-data type — creates enough data via the platform
 * {@link DataSourceRouter#decide(int)} chose to satisfy a data-availability shortfall detected
 * by {@link DataAvailabilityGuard#selectOrBackfill}. Implementations live under
 * {@code com.gsl.database.finance} (e.g. {@code PendingArDocumentBackfillStrategy},
 * {@code PendingApDocumentBackfillStrategy}, {@code LedgerMasterBackfillStrategy},
 * {@code GeneralVoucherEditRevertBackfillStrategy}).
 */
public interface BackfillStrategy {

    /** Creates enough data via the given platform to satisfy neededQuantity, then returns. Never called with K6BULK. */
    void backfill(ApiPlatform platform, int neededQuantity);

    /** Which k6 scenario script backs this document type's bulk path (used only when DataSourceRouter picks K6BULK). */
    String k6ScriptKey();
}
