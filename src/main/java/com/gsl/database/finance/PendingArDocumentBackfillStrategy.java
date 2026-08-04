package com.gsl.database.finance;

import com.aventstack.extentreports.ExtentTest;
import com.gsl.api.core.ApiSessionBundle;
import com.gsl.api.core.ApiSessionFactory;
import com.gsl.api.integration.DcDataSetup;
import com.gsl.api.integration.GrcDataSetup;
import com.gsl.api.integration.ItemDataSetup;
import com.gsl.api.model.common.PricingData;
import com.gsl.api.model.dc.DcCreateResponse;
import com.gsl.api.model.grc.GrcCreateResponse;
import com.gsl.api.model.item.ItemCreateResponse;
import com.gsl.database.BackfillStrategy;
import com.gsl.enums.ApiPlatform;
import com.gsl.enums.ItemManagementMode;
import com.gsl.reports.ApiCallReportLogger;
import com.gsl.reports.BackfillStepLabel;
import com.gsl.reports.ReportLogger;
import com.gsl.reports.StepExecutor;

/**
 * AR Voucher's document-payment shortfall backfill: a fresh Item -> GRC -> DC chain. DC lands
 * in v_pending_adj with debit_credit='D' — exactly the shape DocumentSelector.selectAtomic()
 * is searching for.
 */
public final class PendingArDocumentBackfillStrategy implements BackfillStrategy {

    private static final int DEFAULT_VENDOR_CODE = 206; // matches the vendor k6's createGrc.js already uses successfully
    private static final int DEFAULT_QTY = 5;
    private static final int DEFAULT_RATE = 500;

    private final int siteCode;
    private final int ouCode;
    private final int inventoryGroupCode;

    public PendingArDocumentBackfillStrategy(int siteCode, int ouCode, int inventoryGroupCode) {
        this.siteCode = siteCode;
        this.ouCode = ouCode;
        this.inventoryGroupCode = inventoryGroupCode;
    }

    @Override
    public void backfill(ApiPlatform platform, int neededQuantity) {
        ApiSessionBundle session = ApiSessionFactory.create(platform);
        ApiCallReportLogger.setSection("PreRequisites — Data Creation");
        try {
            for (int i = 0; i < neededQuantity; i++) {
                final int idx = i;
                ExtentTest recordNode = StepExecutor.group("Record " + (idx + 1) + "/" + neededQuantity);

                ItemCreateResponse[] itemHolder = new ItemCreateResponse[1];
                StepExecutor.stepUnder(recordNode, BackfillStepLabel.CREATE_ITEM.label(), () ->
                        itemHolder[0] = ItemDataSetup.setupItemForTest(
                                session.getItemApiClient(), "ARBACKFILL",
                                ItemManagementMode.INDIVIDUAL,
                                PricingData.jittered(DEFAULT_RATE, DEFAULT_RATE + 100, DEFAULT_RATE + 200, DEFAULT_RATE + 300,
                                        Thread.currentThread().getId(), idx),
                                inventoryGroupCode));
                ItemCreateResponse item = itemHolder[0];

                GrcCreateResponse[] grcHolder = new GrcCreateResponse[1];
                StepExecutor.stepUnder(recordNode, BackfillStepLabel.CREATE_GRC.label(), () ->
                        grcHolder[0] = GrcDataSetup.setupGrcForTest(
                                session.getGrcApiClient(), item.getItemCode(), DEFAULT_VENDOR_CODE,
                                siteCode, ouCode, DEFAULT_QTY, DEFAULT_RATE));
                GrcCreateResponse grc = grcHolder[0];

                DcCreateResponse[] dcHolder = new DcCreateResponse[1];
                StepExecutor.stepUnder(recordNode, BackfillStepLabel.CREATE_DC.label(), () ->
                        dcHolder[0] = DcDataSetup.setupDcForTest(
                                session.getDcApiClient(), item.getItemCode(), DEFAULT_QTY, DEFAULT_RATE));
                DcCreateResponse dc = dcHolder[0];

                ReportLogger.dataBackfill("[PendingArDocumentBackfillStrategy] Backfilled record {}/{} — itemCode={}, grcCode={}, dcCode={}",
                        idx + 1, neededQuantity, item.getItemCode(), grc.getGrcCode(), dc.getDcCode());
            }
        } finally {
            ApiCallReportLogger.clearSection();
        }
    }

    @Override
    public String k6ScriptKey() {
        return "createDc";
    }
}
