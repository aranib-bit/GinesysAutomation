package com.gsl.api.bulk;

/**
 * Launches a bulk data-creation run and blocks until it completes. Used by
 * {@link com.gsl.database.DataAvailabilityGuard#selectOrBackfill} when
 * {@code DataSourceRouter.decide(int)} picks the k6-bulk platform for a large data-availability
 * shortfall, instead of routing through a {@link com.gsl.database.BackfillStrategy}'s own
 * WEBAPI/PUBLICAPI path. {@link com.gsl.api.bulk.K6ProcessBulkDataProvisioner} is the only
 * current implementation.
 */
public interface BulkDataProvisioner {
    /**
     * Runs the k6 scenario for {@code scriptKey} and blocks the calling thread until it exits.
     *
     * @param scriptKey identifies which k6 scenario script to run — comes from
     *                  {@link com.gsl.database.BackfillStrategy#k6ScriptKey()}, e.g.
     *                  {@code "createDc"}, {@code "serviceInvoiceAdhoc"}
     * @param quantity  how many records the run should produce
     */
    void provisionAndWait(String scriptKey, int quantity);
}
