package com.gsl.api.integration;

import com.gsl.api.client.DcApiClient;
import com.gsl.api.model.dc.DcCreateResponse;
import com.gsl.api.validator.DcApiValidator;
import com.gsl.reports.ReportLogger;

/**
 * Hybrid API+DB setup for DC (Delivery Challan for an item): create via {@link DcApiClient},
 * validate the response. DB-side row confirmation deferred pending live-schema access — see
 * {@code ItemDataSetup}.
 *
 * <p>Last link of the Item &rarr; GRC &rarr; DC chain: takes the same {@code itemCode} used by
 * {@link GrcDataSetup#setupGrcForTest} (this method itself has no direct dependency on the GRC
 * row, only on the item existing), and the resulting {@code dcCode} is what {@link
 * TransferOutDataSetup#setupTransferOutForTest} needs next. See {@link ItemDataSetup}'s javadoc
 * for the caveat that the strategy assembling this chain isn't currently wired up to any caller.
 */
public final class DcDataSetup {

    private DcDataSetup() {}

    /**
     * Creates a single-line DC for {@code itemCode} via {@code client} and validates the response.
     *
     * @param itemCode the item code returned by an earlier {@link ItemDataSetup#setupItemForTest} call
     * @throws com.gsl.api.core.ApiException if the response fails {@link DcApiValidator}'s checks
     */
    public static DcCreateResponse setupDcForTest(DcApiClient client, String itemCode, int quantity, int rate) {
        DcCreateResponse response = new com.gsl.service.DcService(client).createDc(itemCode, quantity, rate);
        DcApiValidator.validate(response);
        ReportLogger.business("[DcDataSetup] DC created via API — dcCode={}, packetBarcode={}",
                response.getDcCode(), response.getPacketBarcode());
        return response;
    }
}
