package com.gsl.api.integration;

import com.gsl.api.client.DcApiClient;
import com.gsl.api.model.dc.DcCreateResponse;
import com.gsl.api.validator.DcApiValidator;
import com.gsl.reports.ReportLogger;

/**
 * Hybrid API+DB setup for DC: create via {@link DcApiClient}, validate the response.
 * DB-side row confirmation deferred pending live-schema access — see {@code ItemDataSetup}.
 */
public final class DcDataSetup {

    private DcDataSetup() {}

    public static DcCreateResponse setupDcForTest(DcApiClient client, String itemCode, int quantity, int rate) {
        DcCreateResponse response = client.createDc(itemCode, quantity, rate);
        DcApiValidator.validate(response);
        ReportLogger.business("[DcDataSetup] DC created via API — dcCode={}, packetBarcode={}",
                response.getDcCode(), response.getPacketBarcode());
        return response;
    }
}
