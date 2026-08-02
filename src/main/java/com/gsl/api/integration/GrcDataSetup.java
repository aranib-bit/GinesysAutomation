package com.gsl.api.integration;

import com.gsl.api.client.GrcApiClient;
import com.gsl.api.model.grc.GrcCreateResponse;
import com.gsl.api.validator.GrcApiValidator;
import com.gsl.reports.ReportLogger;

/**
 * Hybrid API+DB setup for GRC: create via {@link GrcApiClient}, validate the response.
 * DB-side row confirmation deferred pending live-schema access — see {@code ItemDataSetup}'s
 * javadoc for the same caveat; add a GrcDao here once the GRC header table is confirmed.
 */
public final class GrcDataSetup {

    private GrcDataSetup() {}

    public static GrcCreateResponse setupGrcForTest(GrcApiClient client, String itemCode, int vendorCode,
                                                      int siteCode, int ouCode, int quantity, int rate) {
        GrcCreateResponse response = client.createGrc(itemCode, vendorCode, siteCode, ouCode, quantity, rate);
        GrcApiValidator.validate(response);
        ReportLogger.business("[GrcDataSetup] GRC created via API — grcCode={}, schemeDocno={}",
                response.getGrcCode(), response.getSchemeDocno());
        return response;
    }
}
