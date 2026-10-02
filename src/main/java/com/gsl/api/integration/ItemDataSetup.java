package com.gsl.api.integration;

import com.gsl.api.client.ItemApiClient;
import com.gsl.api.core.DataSetupException;
import com.gsl.api.core.UniqueNameGenerator;
import com.gsl.api.model.common.PricingData;
import com.gsl.api.model.item.ItemCreateResponse;
import com.gsl.api.model.item.ItemValidationResult;
import com.gsl.api.validator.ItemApiValidator;
import com.gsl.enums.ItemManagementMode;
import com.gsl.reports.ReportLogger;

/**
 * Hybrid API+DB setup for Item: create via {@link ItemApiClient} (API — complex, multi-lookup),
 * validate the response (success + non-blank item code via {@link ItemApiValidator}).
 *
 * <p>First link of the Item &rarr; GRC &rarr; DC inventory chain: the returned {@code itemCode}
 * feeds {@link GrcDataSetup#setupGrcForTest} and {@link DcDataSetup#setupDcForTest}. That full
 * chain is currently only assembled by {@code com.gsl.database.finance.PendingArDocumentBackfillStrategy},
 * which is itself no longer constructed anywhere in this codebase (superseded by {@code
 * ApVoucherArBackfillStrategy} — see that strategy's class Javadoc for why); no other caller of
 * this method exists today.
 *
 * <p><b>Follow-up, not yet done here</b>: a dedicated {@code ItemDao} querying the item-master
 * table (to confirm the API-created row actually landed in the DB, the "D" half of hybrid
 * API+DB setup) needs the live schema confirmed first — this build had no DB access to verify
 * the exact table/column names, so it is not guessed at. Add {@code ItemDao.findByCode(...)}
 * here once that table is confirmed, following the same {@code @RegisterBeanMapper} pattern
 * already used by {@code InvGrpDao}/{@code FinVchMainDao}.
 */
public final class ItemDataSetup {

    private ItemDataSetup() {}

    /**
     * Creates a throwaway Item via {@code client}, using a unique name derived from
     * {@code namePrefix}, and validates the response before returning it.
     *
     * @param namePrefix          prefix fed into {@link UniqueNameGenerator#itemName} to avoid name collisions
     * @param inventoryGroupCode  the {@code invgrp} the new item is created under
     * @throws DataSetupException if the API response fails {@link ItemApiValidator}'s checks
     */
    public static ItemCreateResponse setupItemForTest(ItemApiClient client, String namePrefix,
                                                        ItemManagementMode mode, PricingData pricing,
                                                        int inventoryGroupCode) {
        String itemName = UniqueNameGenerator.itemName(namePrefix, 0);

        ItemCreateResponse response = client.createItemWithDefaults(itemName, mode, pricing, inventoryGroupCode);

        ItemValidationResult validation = ItemApiValidator.validate(response);
        if (!validation.isValid()) {
            throw new DataSetupException("Item creation via API failed validation: " + validation.getReason());
        }

        ReportLogger.business("[ItemDataSetup] Item created via API — itemCode={}, name={}",
                response.getItemCode(), itemName);
        return response;
    }
}
