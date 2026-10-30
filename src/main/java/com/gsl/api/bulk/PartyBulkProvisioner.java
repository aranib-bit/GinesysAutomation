package com.gsl.api.bulk;

import com.gsl.api.client.CustomerApiClient;
import com.gsl.api.client.SubLedgerOthersApiClient;
import com.gsl.api.common.PartyKind;
import com.gsl.api.client.VendorApiClient;
import com.gsl.api.common.PartyClassRef;
import com.gsl.api.core.DataSetupException;
import com.gsl.api.webapi.client.PartyMasterWebApiClient;
import com.gsl.api.webapi.model.response.party.PartyListItem;
import com.gsl.service.party.PartyContext;
import lombok.Value;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Bulk party creation through k6, end to end: Java writes the create bodies ({@link PartyBulkDataFile}), k6 posts them
 * ({@code scenarios/backfill/createParty.js}), and Java reads the party list back to count what actually exists, so the result does not rest
 * on k6's own counters alone. The data file is removed after a clean run and kept after a failed one for diagnosis.
 */
public final class PartyBulkProvisioner {

    /** Both numbers come from independent sources: {@code k6} is k6's own report, {@code found} is the party list read afterwards. */
    @Value
    public static class Result {
        K6Result k6;
        int requested;
        int found;

        /** k6 ended cleanly and every requested party is in the master. */
        public boolean complete() {
            return k6.passed() && found == requested;
        }
    }

    private PartyBulkProvisioner() {}

    /**
     * @param vus how many users post in parallel ({@code TARGET_VUS}); 0 keeps the scenario default
     * @throws DataSetupException when k6 cannot be started (see {@link K6Runner}) or the data file cannot be handled
     */
    public static Result create(PartyClassRef cls, int count, int vus, PartyContext c, String runId, VendorApiClient vendors,
                                CustomerApiClient customers, PartyMasterWebApiClient lists, String tenant, Consumer<String> lineHandler) {
        Path dataFile = PartyBulkDataFile.write(cls, count, c, runId, vendors, customers, lists);
        K6Result k6 = runK6(dataFile, count, vus, tenant, lineHandler);
        return finish(dataFile, new Result(k6, count, countRun(lists.list(cls.family(), c.site(), c.ou(), c.workDate()), runId)));
    }

    /**
     * Creates {@code count} Sub Ledger "Others" linked to {@code glcode}. There is no Others list call captured yet, so {@code found} is k6's own
     * created count (the server accepted each create), not an independent read-back.
     */
    public static Result createOthers(int count, int vus, long glcode, String glname, Long siteCode, String runId,
                                      SubLedgerOthersApiClient others, String tenant, Consumer<String> lineHandler) {
        Path dataFile = PartyBulkDataFile.writeOthers(count, glcode, glname, siteCode, runId, others);
        K6Result k6 = runK6(dataFile, count, vus, tenant, lineHandler);
        return finish(dataFile, new Result(k6, count, Math.max(k6.getCreated(), 0)));
    }

    /**
     * Marks this run's own vendor-family parties extinct (or active again). Never touches a party that is not from the run. {@code found} is
     * the number of the run's parties the list now shows in the requested state.
     */
    public static Result setExtinct(boolean extinct, int vus, PartyContext c, String runId, VendorApiClient vendors,
                                    PartyMasterWebApiClient lists, String tenant, Consumer<String> lineHandler) {
        Path dataFile = PartyBulkDataFile.writeExtinct(extinct, c, runId, vendors, lists);
        int expected = BulkDataFiles.count(dataFile, "bodies");
        K6Result k6 = runK6(dataFile, expected, vus, tenant, lineHandler);
        List<PartyListItem> after = lists.list(PartyKind.Family.VENDOR_MASTER, c.site(), c.ou(), c.workDate());
        return finish(dataFile, new Result(k6, expected, countRunInState(after, runId, extinct)));
    }

    private static K6Result runK6(Path dataFile, int count, int vus, String tenant, Consumer<String> lineHandler) {
        return K6Runner.configured().run(K6Scripts.resolve("bulkPost"), env(dataFile, count, vus, tenant), lineHandler);
    }

    private static Result finish(Path dataFile, Result result) {
        if (result.complete()) BulkDataFiles.deleteQuietly(dataFile);
        return result;
    }

    /** The run's parties in {@code listed} whose extinct flag equals {@code extinct}. */
    public static int countRunInState(List<PartyListItem> listed, String runId, boolean extinct) {
        String prefix = PartyBulkDataFile.slIdPrefix(runId);
        return (int) listed.stream().filter(p -> p.getSlId() != null && p.getSlId().startsWith(prefix) && p.isExtinct() == extinct).count();
    }

    /** The parties of this run in {@code listed}: those whose sub-ledger id carries the run's prefix. */
    public static int countRun(List<PartyListItem> listed, String runId) {
        String prefix = PartyBulkDataFile.slIdPrefix(runId);
        return (int) listed.stream().filter(p -> p.getSlId() != null && p.getSlId().startsWith(prefix)).count();
    }

    /** The environment the createParty scenario reads. */
    public static Map<String, String> env(Path dataFile, int count, int vus, String tenant) {
        Map<String, String> env = new LinkedHashMap<>();
        env.put("BULK_DATA_FILE", dataFile.toAbsolutePath().toString());
        env.put("TARGET_DOCS", String.valueOf(count));
        env.put("TENANT", tenant);
        if (vus > 0) env.put("TARGET_VUS", String.valueOf(vus));
        return env;
    }
}
