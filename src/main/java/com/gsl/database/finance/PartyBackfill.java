package com.gsl.database.finance;

import com.gsl.api.client.ApVoucherApiClient;
import com.gsl.api.core.ApiException;
import com.gsl.api.core.ApiSessionBundle;
import com.gsl.api.model.common.SubLedgerLookupResult;
import com.gsl.service.party.PartyContext;
import com.gsl.service.party.PartyMasterGateways;
import com.gsl.service.party.PartyMasterService;
import org.testng.SkipException;

/**
 * Makes sure the Finance sub-ledger lookup offers a party of a required class before a voucher is raised against it
 * ({@link ApVoucherArBackfillStrategy}); the creation itself is {@link PartyMasterService}'s job (platform order from
 * {@code backfill.party.platformOrder}). Works with or without the DB.
 */
public final class PartyBackfill {

    private PartyBackfill() {}

    /**
     * @throws SkipException when no platform can create a party of the class
     * @throws ApiException  when the party was created but never showed up in the sub-ledger lookup
     */
    public static void ensurePartyOfClass(ApiSessionBundle session, long clsCode, int site, int ou, long empCode, String workDate) {
        ApVoucherApiClient finance = session.getApVoucherApiClient();
        new PartyMasterService(PartyMasterGateways.ordered(session)).ensurePartyOfClass(clsCode, new PartyContext(site, ou, workDate),
                () -> offered(finance, clsCode, site, ou, empCode, workDate));
    }

    /**
     * Creates a NEW party of the class and returns it as the Finance sub-ledger lookup shows it, so a voucher can be raised against a sub-ledger no other
     * scenario holds documents of.
     *
     * @throws SkipException when no platform can create a party of the class
     * @throws ApiException  when the party was created but never showed up in the sub-ledger lookup
     */
    public static SubLedgerLookupResult createFreshPartyOfClass(ApiSessionBundle session, long clsCode, int site, int ou, long empCode, String workDate) {
        ApVoucherApiClient finance = session.getApVoucherApiClient();
        PartyMasterService.CreatedParty created = new PartyMasterService(PartyMasterGateways.ordered(session)).createParty(clsCode,
                new PartyContext(site, ou, workDate), party -> () -> bySlid(finance, party.slId(), site, ou, empCode, workDate) != null);
        return java.util.Objects.requireNonNull(bySlid(finance, created.slId(), site, ou, empCode, workDate));
    }

    private static SubLedgerLookupResult bySlid(ApVoucherApiClient finance, String slId, int site, int ou, long empCode, String workDate) {
        for (SubLedgerLookupResult sl : finance.fetchSubLedgers(site, ou, empCode, workDate)) {
            if (slId.equals(sl.getSlid())) return sl;
        }
        return null;
    }

    /** True when the Finance sub-ledger lookup lists a party of the class (the lookup decides what a voucher can be raised against). */
    private static boolean offered(ApVoucherApiClient finance, long clsCode, int site, int ou, long empCode, String workDate) {
        for (SubLedgerLookupResult sl : finance.fetchSubLedgers(site, ou, empCode, workDate)) {
            if (sl.getClassCode() == clsCode) return true;
        }
        return false;
    }
}
