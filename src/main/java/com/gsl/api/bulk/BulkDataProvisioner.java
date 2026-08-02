package com.gsl.api.bulk;

/** Launches a bulk data-creation run and blocks until it completes. */
public interface BulkDataProvisioner {
    /** scriptKey comes from BackfillStrategy.k6ScriptKey() — e.g. "createDc", "serviceInvoiceAdhoc". */
    void provisionAndWait(String scriptKey, int quantity);
}
