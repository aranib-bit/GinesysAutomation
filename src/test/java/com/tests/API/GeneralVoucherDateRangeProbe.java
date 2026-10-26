package com.tests.API;

import com.gsl.api.client.GeneralVoucherApiClient;
import com.gsl.api.model.common.UserSiteContext;
import com.gsl.api.model.generalvoucher.GeneralVoucherCreateResponse;
import com.gsl.api.model.generalvoucher.GeneralVoucherRootView;
import com.gsl.enums.GlAccountType;
import com.gsl.reports.ReportLogger;
import com.gsl.utils.ValidDateRangeFinder;
import com.tests.APITest;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.time.LocalDate;

/**
 * Live probe (run on demand, not part of any suite): confirms that {@code POST /vouchers/general/validations/voucherdate} — called for an
 * EXISTING voucher with its own accounting year as {@code OldYCODE} — rejects dates outside that year, so
 * {@link GeneralVoucherApiClient#findValidVoucherDateRange} can stand in for the {@code admyear} reads the DB Edit data source uses. Creates
 * one voucher, reverts it to Unposted if the application auto-posted it, probes, and always removes it again. Fails if either edge is
 * unbounded (the validator is not year-aware, so the approach does not work).
 *
 * <p>{@code -Dprobe.cleanup=<vchcode>[,<vchcode>...]} additionally reverts and deletes vouchers a previous probe run left behind.
 */
public class GeneralVoucherDateRangeProbe extends APITest {

    @Test
    public void validatorRejectsDatesOutsideTheAccountingYear() {
        GeneralVoucherApiClient api = session().getGeneralVoucherApiClient();
        long empCode = Long.parseLong(session().getUserId());
        long ouCode = api.fetchUserContext().getOucode();

        String leaked = System.getProperty("probe.cleanup", "");
        for (String code : leaked.split(",")) {
            if (!code.isBlank()) removeVoucher(api, Long.parseLong(code.trim()), empCode, ouCode);
        }

        GeneralVoucherCreateResponse created = api.createGeneralVoucherWithDefaults(GlAccountType.ASSET, 0, 0, empCode, false);
        long vchcode = created.vchcode();
        try {
            GeneralVoucherRootView view = api.fetchRootView(vchcode, ouCode, empCode);
            ReportLogger.business("[PROBE] created " + created.getDocScheme() + " vchcode=" + vchcode + " documentStatus="
                    + view.getDocumentStatus() + " voucherDate=" + view.getVoucherDate() + " yCode=" + view.getYCode()
                    + " (requested release=false)");
            ReportLogger.business("[PROBE] rootview unmapped fields: " + view.getExtras());
            if (view.isPosted()) {
                api.revertGeneralVoucherOrThrow(vchcode, empCode, ouCode);
                view = api.fetchRootView(vchcode, ouCode, empCode);
                ReportLogger.business("[PROBE] after revert: documentStatus=" + view.getDocumentStatus());
            }

            LocalDate anchor = LocalDate.parse(view.getVoucherDate().substring(0, 10));
            ValidDateRangeFinder.Range range = api.findValidVoucherDateRange(empCode, anchor, ouCode, vchcode, view.getYCode());
            ReportLogger.business("[PROBE] EXISTING voucher " + created.getDocScheme() + " (yCode=" + view.getYCode() + "): accepted "
                    + range.first() + " .. " + range.last() + " (firstBounded=" + range.firstBounded() + ", lastBounded="
                    + range.lastBounded() + ", probes=" + range.probes() + ", span=" + ValidDateRangeFinder.spanDays(range) + " days)");

            boolean dayAfterRejected = !api.isVoucherDateAccepted(empCode, range.last().plusDays(1), ouCode, vchcode, view.getYCode());
            boolean dayBeforeRejected = !api.isVoucherDateAccepted(empCode, range.first().minusDays(1), ouCode, vchcode, view.getYCode());
            ReportLogger.business("[PROBE] day after last rejected=" + dayAfterRejected + ", day before first rejected=" + dayBeforeRejected);

            Assert.assertTrue(range.fullyBounded(),
                    "validateVoucherDate never rejected a date within 400 days of the voucher date even with OldYCODE set — it is not "
                    + "accounting-year aware, so year edges cannot be found this way: " + range);
            Assert.assertTrue(dayAfterRejected && dayBeforeRejected, "edge dates are not a clean accepted/rejected boundary: " + range);
            Assert.assertTrue(ValidDateRangeFinder.spanDays(range) <= 380,
                    "accepted range implausibly long for an accounting year: " + range);
        } finally {
            removeVoucher(api, vchcode, empCode, ouCode);
        }
    }

    private static void removeVoucher(GeneralVoucherApiClient api, long vchcode, long empCode, long ouCode) {
        GeneralVoucherRootView v = api.fetchRootView(vchcode, ouCode, empCode);
        if (v != null && v.isPosted()) api.revertGeneralVoucherOrThrow(vchcode, empCode, ouCode);
        api.deleteGeneralVoucherOrThrow(vchcode, empCode, ouCode);
        ReportLogger.business("[PROBE] removed vchcode=" + vchcode);
    }
}
