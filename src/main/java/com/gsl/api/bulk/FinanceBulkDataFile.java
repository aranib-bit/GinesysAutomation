package com.gsl.api.bulk;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gsl.api.client.LedgerApiClient;
import com.gsl.api.model.ledger.LedgerCreateRequest;
import com.gsl.api.service.ChequeBookService;
import com.gsl.api.webapi.factory.ChequeBookRequestFactory;
import com.gsl.api.webapi.model.request.chequebook.DuplicateChequeRequest;
import com.gsl.enums.GlAccountType;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Data files for the Finance masters k6 creates in bulk: ledgers (one body per iteration) and cheque books (candidate ranges per book). */
public final class FinanceBulkDataFile {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private FinanceBulkDataFile() {}

    /** The ledger abbreviation of a run starts with this; lets the run's ledgers be recognised. */
    public static String ledgerAbbrePrefix(String runId) {
        return "ZG" + runId;
    }

    /** {@code count} ledgers of one combination ({@code Ledger/Create}), bodies from {@link LedgerApiClient#buildBulkRequests}. */
    public static Path writeLedgers(GlAccountType type, boolean subLedgerApplicable, boolean siteApplicable, Collection<Long> assignedSiteCodes,
                                    int siteCount, int count, String runId, LedgerApiClient ledgers) {
        List<LedgerCreateRequest> requests = ledgers.buildBulkRequests(type, subLedgerApplicable, siteApplicable, assignedSiteCodes, siteCount, count, runId);
        List<Object> bodies = new ArrayList<>(requests.size());
        for (LedgerCreateRequest r : requests) bodies.add(MAPPER.convertValue(r, Map.class));
        Map<String, Object> file = header("CreateLedger", "Ledger", "CREATE", runId);
        file.put("bodies", bodies);
        return BulkDataFiles.write("ledgers", runId, file);
    }

    /**
     * {@code bookCount} cheque books for {@code bankGlCode}. Each book gets {@code settings.maxRangeAttempts()} candidate ranges, none of them
     * shared with another book, each with its duplicate check and create body; k6 takes the first free one.
     */
    public static Path writeChequeBooks(long bankGlCode, long ownerSiteCode, long empCode, String workDate, int bookCount, String runId,
                                        ChequeBookService.Settings settings) {
        int leaves = settings.leafCount();
        List<Object> books = new ArrayList<>(bookCount);
        for (int b = 0; b < bookCount; b++) {
            List<Object> attempts = new ArrayList<>(settings.maxRangeAttempts());
            for (int a = 0; a < settings.maxRangeAttempts(); a++) {
                int start = Math.addExact(settings.startNumber(), Math.multiplyExact(Math.addExact(Math.multiplyExact(b, settings.maxRangeAttempts()), a), leaves));
                int end = Math.addExact(start, leaves - 1);
                Map<String, Object> attempt = new LinkedHashMap<>();
                attempt.put("start", start);
                attempt.put("duplicateCheck", MAPPER.convertValue(DuplicateChequeRequest.of(bankGlCode, start, end, settings.width()), Map.class));
                attempt.put("create", MAPPER.convertValue(
                        ChequeBookRequestFactory.create(ownerSiteCode, empCode, bankGlCode, workDate, start, leaves, settings.width()), Map.class));
                attempts.add(attempt);
            }
            books.add(Map.of("attempts", attempts));
        }
        Map<String, Object> file = header("CreateChequeBook", "Cheque", "CREATE", runId);
        file.put("glCode", bankGlCode);
        file.put("books", books);
        return BulkDataFiles.write("cheques", runId, file);
    }

    private static Map<String, Object> header(String logTag, String group, String endpoint, String runId) {
        Map<String, Object> file = new LinkedHashMap<>();
        file.put("logTag", logTag);
        file.put("endpointGroup", group);
        file.put("endpoint", endpoint);
        file.put("runId", runId);
        return file;
    }
}
