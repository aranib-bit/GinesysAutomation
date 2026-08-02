package com.gsl.database;

import com.gsl.enums.ApiPlatform;

/** One per document type — creates enough data via the given platform to satisfy a shortfall. */
public interface BackfillStrategy {

    /** Creates enough data via the given platform to satisfy neededQuantity, then returns. Never called with K6BULK. */
    void backfill(ApiPlatform platform, int neededQuantity);

    /** Which k6 scenario script backs this document type's bulk path (used only when DataSourceRouter picks K6BULK). */
    String k6ScriptKey();
}
