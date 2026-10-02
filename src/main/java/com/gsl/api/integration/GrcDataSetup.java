package com.gsl.api.integration;

import com.gsl.api.client.GrcApiClient;
import com.gsl.api.model.grc.GrcCreateResponse;
import com.gsl.api.validator.GrcApiValidator;
import com.gsl.reports.ReportLogger;

/**
 * Hybrid API+DB setup for GRC (Goods Receipt against a vendor): create via {@link GrcApiClient},
 * validate the response. DB-side row confirmation deferred pending live-schema access — see
 * {@code ItemDataSetup}'s javadoc for the same caveat; add a GrcDao here once the GRC header
 * table is confirmed.
 *
 * <p>Middle link of the Item &rarr; GRC &rarr; DC chain: takes the {@code itemCode} produced by
 * {@link ItemDataSetup#setupItemForTest}, and the resulting {@code grcCode}/{@code schemeDocno}
 * feed the next link, {@link DcDataSetup#setupDcForTest}. See {@link ItemDataSetup}'s javadoc for
 * the caveat that the strategy assembling this chain isn't currently wired up to any caller.
 */
public final class GrcDataSetup {

    private GrcDataSetup() {}

    /**
     * Creates a single-line GRC for {@code itemCode} via {@code client} and validates the response.
     *
     * @param itemCode the item code returned by an earlier {@link ItemDataSetup#setupItemForTest} call
     * @throws com.gsl.api.core.ApiException if the response fails {@link GrcApiValidator}'s checks
     */
    public static GrcCreateResponse setupGrcForTest(GrcApiClient client, String itemCode, int vendorCode,
                                                      int siteCode, int ouCode, int quantity, int rate) {
        GrcCreateResponse response = client.createGrc(itemCode, vendorCode, siteCode, ouCode, quantity, rate);
        GrcApiValidator.validate(response);
        ReportLogger.business("[GrcDataSetup] GRC created via API — grcCode={}, schemeDocno={}",
                response.getGrcCode(), response.getSchemeDocno());
        return response;
    }
}
