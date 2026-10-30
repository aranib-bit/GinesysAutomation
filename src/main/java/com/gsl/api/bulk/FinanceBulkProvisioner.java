package com.gsl.api.bulk;

import com.gsl.api.client.LedgerApiClient;
import com.gsl.api.service.ChequeBookService;
import com.gsl.enums.GlAccountType;

import java.nio.file.Path;
import java.util.Collection;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.function.Consumer;

/**
 * Finance master bulk creation through k6: Java writes the data file, k6 posts it, and the result reports k6's own created/failed counts
 * (no list call is read back for ledgers or cheque books yet, so {@code created} is the server accepting each create).
 */
public final class FinanceBulkProvisioner {

    private FinanceBulkProvisioner() {}

    /** Creates {@code count} ledgers of one combination. */
    public static K6Result createLedgers(GlAccountType type, boolean subLedgerApplicable, boolean siteApplicable, Collection<Long> assignedSiteCodes,
                                         int siteCount, int count, int vus, String runId, LedgerApiClient ledgers, String tenant,
                                         Consumer<String> lineHandler) {
        Path file = FinanceBulkDataFile.writeLedgers(type, subLedgerApplicable, siteApplicable, assignedSiteCodes, siteCount, count, runId, ledgers);
        return run("bulkPost", file, count, vus, tenant, lineHandler);
    }

    /** Creates {@code bookCount} cheque books for the bank ledger, each in the first free range of its own candidate list. */
    public static K6Result createChequeBooks(long bankGlCode, long ownerSiteCode, long empCode, String workDate, int bookCount, int vus,
                                             String runId, String tenant, Consumer<String> lineHandler) {
        Path file = FinanceBulkDataFile.writeChequeBooks(bankGlCode, ownerSiteCode, empCode, workDate, bookCount, runId, ChequeBookService.Settings.fromConfig());
        return run("createChequeBook", file, bookCount, vus, tenant, lineHandler);
    }

    private static K6Result run(String scriptKey, Path file, int count, int vus, String tenant, Consumer<String> lineHandler) {
        Map<String, String> env = new LinkedHashMap<>(PartyBulkProvisioner.env(file, count, vus, tenant));
        K6Result result = K6Runner.configured().run(K6Scripts.resolve(scriptKey), env, lineHandler);
        if (result.passed() && result.getCreated() == count) BulkDataFiles.deleteQuietly(file);
        return result;
    }
}
