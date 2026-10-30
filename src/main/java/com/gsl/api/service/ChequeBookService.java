package com.gsl.api.service;

import com.gsl.api.core.ApiException;
import com.gsl.api.webapi.client.ChequeBookApiClient;
import com.gsl.api.webapi.factory.ChequeBookRequestFactory;
import com.gsl.api.webapi.model.request.chequebook.DuplicateChequeRequest;
import com.gsl.config.ApiConfig;
import com.gsl.config.ConfigFactory;
import com.gsl.reports.ReportLogger;

import java.util.List;

/**
 * The cheque-range search: finds a free range for a bank GL and creates a cheque book in it. The client is injected (a fake in unit tests).
 * Duplicate-check followed by create is not atomic, so a create refused as a duplicate range moves on to the next range; every other
 * failure (authentication, validation, server error) propagates untouched.
 */
public class ChequeBookService {

    /** The tunable search settings (from {@code ApiConfig} by default). */
    public record Settings(int leafCount, int width, int startNumber, int maxRangeAttempts) {
        public static Settings fromConfig() {
            ApiConfig cfg = ConfigFactory.getApiConfig();
            return new Settings(Math.max(1, cfg.chequeBookLeafCount()), cfg.chequeBookWidth(), cfg.chequeBookStartNumber(),
                    Math.max(1, cfg.chequeBookMaxRangeAttempts()));
        }
    }

    private final ChequeBookApiClient client;
    private final Settings settings;

    public ChequeBookService(ChequeBookApiClient client) {
        this(client, Settings.fromConfig());
    }

    public ChequeBookService(ChequeBookApiClient client, Settings settings) {
        this.client = client;
        this.settings = settings;
    }

    /**
     * Creates one cheque book of {@code leafCount} leaves for {@code bankGlCode}, moving the range up in leaf-count steps until
     * {@code duplicatecheques} reports it free.
     *
     * @return the number of the first leaf created
     * @throws ApiException when no free range is found within {@code maxRangeAttempts} attempts, or on a non-duplicate failure
     */
    public int createFreeChequeBook(long bankGlCode, long ownerSiteCode, long empCode, String workDate) {
        int leaves = settings.leafCount();
        for (int attempt = 0; attempt < settings.maxRangeAttempts(); attempt++) {
            int start = Math.addExact(settings.startNumber(), Math.multiplyExact(attempt, leaves));
            int end = Math.addExact(start, leaves - 1);

            List<Integer> used = client.fetchDuplicateCheques(DuplicateChequeRequest.of(bankGlCode, start, end, settings.width()));
            if (!used.isEmpty()) continue;

            try {
                String book = client.createChequeBook(ChequeBookRequestFactory.create(
                        ownerSiteCode, empCode, bankGlCode, workDate, start, leaves, settings.width()));
                ReportLogger.dataBackfill("[ChequeBookService] Created cheque book {} for glCode={}: {} leaves from {}",
                        book, bankGlCode, leaves, start);
                return start;
            } catch (ApiException e) {
                if (!isDuplicateConflict(e)) throw e;
                ReportLogger.dataBackfillWarn("[ChequeBookService] Range {}-{} was taken between the check and the create - trying the next range.", start, end);
            }
        }
        throw new ApiException("No free cheque range found for glCode=" + bankGlCode + " after " + settings.maxRangeAttempts()
                + " attempts starting at " + settings.startNumber(), 0);
    }

    /** A create refused because another session took the range: HTTP 400/409 whose message mentions a duplicate. */
    private static boolean isDuplicateConflict(ApiException e) {
        int status = e.getStatusCode();
        String message = e.getMessage() == null ? "" : e.getMessage().toLowerCase();
        return (status == 400 || status == 409) && message.contains("duplicate");
    }
}
