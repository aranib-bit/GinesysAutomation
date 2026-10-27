package com.gsl.api.client;

import com.gsl.api.core.ApiClient;
import com.gsl.api.core.ApiException;
import com.gsl.config.ApiConfig;
import com.gsl.config.ConfigFactory;
import com.gsl.reports.ReportLogger;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Finance API client for Cheque Book master creation — the flow captured in
 * {@code CreateChequeBookUsingGenerateLeafwhereduplicateexists.har}:
 * {@code POST /cheques/duplicatecheques} (which numbers of a range are already used for the bank GL) followed by
 * {@code POST /cheques/create} (201). Backs the API-mode cheque shortfall backfill ({@code ApiChequeResolver}).
 */
public class ChequeBookApiClient extends ApiClient {

    /** Wraps an already-authenticated session {@code spec} — see {@link ApiClient}'s class Javadoc. */
    public ChequeBookApiClient(RequestSpecification spec) {
        super(spec);
    }

    /** The Finance API base ({@code …/Finance/api}). */
    protected String financeBase() {
        return ConfigFactory.getApiConfig().financeApiBaseUri();
    }

    /** {@code POST /cheques/duplicatecheques} — the numbers in {@code [start, end]} already used for {@code glCode} (empty = range is free). */
    public List<Integer> fetchDuplicateCheques(long glCode, int start, int end, int width) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("chequeStartNumber", start);
        body.put("chequeEndNumber", end);
        body.put("chequeWidth", width);
        body.put("glCode", glCode);
        Response response = postRaw(financeBase() + "/cheques/duplicatecheques", body);
        List<Integer> dup = response.jsonPath().getList("result", Integer.class);
        return dup == null ? List.of() : dup;
    }

    /** {@code POST /cheques/create} for the leaves {@code [start, start+leafCount)}; returns the new book number reported by the server. */
    public String createChequeBook(long ownerSiteCode, long empCode, long glCode, String workDate, int start, int leafCount, int width) {
        List<Map<String, Object>> details = new ArrayList<>();
        for (int n = start; n < start + leafCount; n++) {
            Map<String, Object> d = new LinkedHashMap<>();
            d.put("CHQNO", String.format("%0" + width + "d", n));
            d.put("CHQDT", null);
            d.put("vchCode", null);
            d.put("Type", "A");
            d.put("Stat", "N");
            details.add(d);
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", 0);
        body.put("ownerSiteCode", ownerSiteCode);
        body.put("dateOfIssue", workDate);
        body.put("remarks", "");
        body.put("empCode", empCode);
        body.put("glcode", glCode);
        body.put("dataversion", 0);
        body.put("chequeDetails", details);
        Response response = postRaw(financeBase() + "/cheques/create", body);
        Boolean success = response.jsonPath().getObject("success", Boolean.class);
        if (success == null || !success) {
            throw new ApiException("cheques/create failed for glCode=" + glCode + ": " + response.asString(), response.statusCode());
        }
        return response.jsonPath().getString("result.item2");
    }

    /**
     * Creates one cheque book of {@code backfill.chequebook.leafCount} leaves for {@code bankGlCode}, moving the range up in
     * leaf-count steps until {@code duplicatecheques} reports it free.
     *
     * @return the number of the first leaf created
     * @throws ApiException when no free range is found within {@code backfill.chequebook.maxRangeAttempts} attempts
     */
    public int createFreeChequeBook(long bankGlCode, long ownerSiteCode, long empCode, String workDate) {
        ApiConfig cfg = ConfigFactory.getApiConfig();
        int leaves = Math.max(1, cfg.chequeBookLeafCount());
        int width = cfg.chequeBookWidth();
        int start = cfg.chequeBookStartNumber();
        for (int attempt = 0; attempt < Math.max(1, cfg.chequeBookMaxRangeAttempts()); attempt++, start += leaves) {
            List<Integer> used = fetchDuplicateCheques(bankGlCode, start, start + leaves - 1, width);
            if (!used.isEmpty()) continue;
            String book = createChequeBook(ownerSiteCode, empCode, bankGlCode, workDate, start, leaves, width);
            ReportLogger.dataBackfill("[ChequeBookApiClient] Created cheque book {} for glCode={}: {} leaves from {}",
                    book, bankGlCode, leaves, start);
            return start;
        }
        throw new ApiException("No free cheque range found for glCode=" + bankGlCode + " after "
                + cfg.chequeBookMaxRangeAttempts() + " attempts starting at " + cfg.chequeBookStartNumber(), 0);
    }
}
