package com.gsl.database.finance;

import com.gsl.database.BackfillStrategy;
import com.gsl.enums.ApiPlatform;
import com.gsl.reports.ReportLogger;
import com.gsl.utils.dataprovider.source.finance.ApiApVoucherLifecycleDataSource;

/**
 * {@code TAGGED}-cell shortfall backfill for the AP Revert/Delete/Edit data sources: creates AP Vouchers that adjust a pending document,
 * which tags them ({@code POST Finance/api/vouchers/ap} with {@code apVoucherAdjustmentDetail} — HAR: APVoucherwithalllookupsDocAdjustment).
 * The pending document each one adjusts is selected through the Document Adjustments API (and itself backfilled when none exists), so
 * no database read is involved.
 *
 * <p>This replaces the earlier stub that threw a {@code SkipException} because no "create + tag" capture existed; that capture is now
 * in hand. The platform is not used: the voucher is always created through the Web API session (no k6/public-API variant exists).
 */
public final class ApVoucherTaggedBackfillStrategy implements BackfillStrategy {

    @Override
    public void backfill(ApiPlatform platform, int neededQuantity) {
        ReportLogger.dataBackfill("[ApVoucherTaggedBackfillStrategy] Creating {} tagged AP Voucher(s) by adjusting pending documents", neededQuantity);
        ApiApVoucherLifecycleDataSource.createTaggedVouchers(neededQuantity);
    }

    /** {@inheritDoc} No k6 script exists for this path; quantities are always small, so K6BULK is never chosen. */
    @Override
    public String k6ScriptKey() {
        return "apVoucherTaggedBackfill";
    }
}
