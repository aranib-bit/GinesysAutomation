package com.gsl.utils.dataprovider.fixture.finance;

import com.gsl.api.client.GeneralVoucherApiClient;
import com.gsl.api.model.arvoucher.SubLedgerLookupResult;

import java.util.List;

/** Waits, through the API only (no DB), for freshly created Sub Ledgers to show up in the {@code /subledgers} lookup of a GL. */
public final class ApiSubLedgerWait {

    /** How long to wait for a freshly created Sub Ledger to appear in the {@code /subledgers} lookup. */
    public static final long VISIBLE_TIMEOUT_MS = 30_000L;
    private static final long POLL_MS = 1_500L;

    private ApiSubLedgerWait() {}

    /**
     * Polls {@code /subledgers?parentGLCode=glCode} until at least {@code minCount} entries are offered or the timeout passes.
     *
     * @return the last lookup read (may hold fewer than {@code minCount} entries on timeout)
     */
    public static List<SubLedgerLookupResult> awaitAtLeast(GeneralVoucherApiClient api, long glCode, int connectedSite, long userCode,
                                                           int ouCode, String workDate, int minCount) {
        long deadline = System.currentTimeMillis() + VISIBLE_TIMEOUT_MS;
        List<SubLedgerLookupResult> subLedgers = List.of();
        while (true) {
            subLedgers = api.fetchSubLedgers(glCode, connectedSite, userCode, ouCode, workDate);
            if (subLedgers != null && subLedgers.size() >= minCount) return subLedgers;
            if (System.currentTimeMillis() >= deadline) return subLedgers == null ? List.of() : subLedgers;
            try {
                Thread.sleep(POLL_MS);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return subLedgers == null ? List.of() : subLedgers;
            }
        }
    }
}
