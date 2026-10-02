package com.gsl.api.client;

import com.gsl.api.client.udf.UdfValueBuilder;
import com.gsl.api.core.ApiClient;
import com.gsl.api.core.ApiException;
import com.gsl.api.model.common.SimpleResultResponse;
import com.gsl.api.model.subledgerothers.FinSlGlEntry;
import com.gsl.api.model.subledgerothers.FinSlGlSiteInfo;
import com.gsl.api.model.subledgerothers.SubLedgerOthersCreateRequest;
import com.gsl.api.model.subledgerothers.SubLedgerOthersCreationOutcome;
import com.gsl.api.model.subledgerothers.SubledgerClassLookupResult;
import com.gsl.api.model.udf.UdfValueEntry;
import com.gsl.reports.ReportLogger;
import com.gsl.reports.StepExecutor;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;

import java.util.List;
import java.util.Map;

/**
 * Finance module API client for Sub Ledger "Others" master creation — mirrors the real flow
 * captured in {@code CreateSubLedgerOthers_stage-ideal.ginesys.cloud4.har}. Backs
 * {@code SubLedgerOthersBackfillStrategy}, General Voucher's Type3 ({@code finsl_gl}) shortfall
 * backfill: {@code CreateFinanceSubLedgerOther}'s payload creates the {@code finsl} row AND its
 * {@code finSLGLs} GL-linkage in a single call.
 *
 * <p>UDF handling reuses {@link UdfValueBuilder} verbatim (table {@code FINSL}) — the exact same
 * fetch/build/mandatory-value logic AR Voucher uses for {@code FINVCHMAIN}, per the plan's
 * confirmed decision to share this logic rather than duplicate it.
 */
public class SubLedgerOthersApiClient extends ApiClient {

    /** Confirmed via HAR: {@code GetSubledgerClassLookup?classType=O} resolves to clscode 6 ("Others") — never hardcoded here. */
    private static final String OTHERS_CLASS_TYPE = "O";

    /** Wraps an already-authenticated session {@code spec} — see {@link ApiClient}'s class Javadoc. */
    public SubLedgerOthersApiClient(RequestSpecification spec) {
        super(spec);
    }

    /** POST /Shared/GetSubledgerClassLookup?classType=O — resolves the "Others" clscode live. */
    public List<SubledgerClassLookupResult> fetchSubledgerClassLookup(String classType) {
        Map<String, Object> params = Map.of("classType", classType);
        Response response = postRaw("/Shared/GetSubledgerClassLookup", Map.of(), params);
        return response.jsonPath().getList("result.items", SubledgerClassLookupResult.class);
    }

    /** POST /EmployeeMaster/CreateFinanceSubLedgerOther — the final save call. */
    public SimpleResultResponse createFinanceSubLedgerOther(SubLedgerOthersCreateRequest request) {
        return post("/EmployeeMaster/CreateFinanceSubLedgerOther", request, SimpleResultResponse.class);
    }

    /**
     * Orchestrates the full flow for a given target GL — same "thread each live response into
     * the next, never hardcode" principle as {@code ArVoucherApiClient.createArVoucherWithDefaults}.
     *
     * @param targetGlcode the {@code fingl.glcode} General Voucher's Type3 combination needs a
     *                     permitted Sub Ledger against (the GV combination's already-resolved GL —
     *                     never hardcoded, unlike the HAR's own sample {@code glcode:2}).
     * @param targetGlname display name for the same GL, echoed into {@code finSLGLs[0].glname}.
     * @param targetSiteCode the {@code admsite.code} (from {@code fingl_site}) to narrow
     *                       {@code finSLGLs[0].finSLGLSites} to — required (non-null) whenever
     *                       {@code targetGlcode}'s GL is Site-Applicable ({@code fingl.costapp =
     *                       'Y'}); the server rejects the create with "Site selection is required
     *                       for the Ledgers - ..." if a Site-Applicable GL is passed with this
     *                       null. Pass {@code null} only when the GL is not Site-Applicable.
     */
    public SubLedgerOthersCreationOutcome createSubLedgerOthersWithDefaults(
            long targetGlcode, String targetGlname, Long targetSiteCode) {

        List<SubledgerClassLookupResult> classes = StepExecutor.call("Get Sub Ledger Class for Others",
                () -> fetchSubledgerClassLookup(OTHERS_CLASS_TYPE));
        if (classes.isEmpty()) {
            throw new ApiException("GetSubledgerClassLookup?classType=" + OTHERS_CLASS_TYPE
                    + " returned no classes — cannot create an Others Sub Ledger.", 0);
        }
        long clscode = classes.get(0).getCode();

        String uniqueSuffix = Long.toString(System.nanoTime(), 36).toUpperCase();
        String slname = "AUTO_SL_" + uniqueSuffix;
        // slid is typically a short, human-entered identifier (HAR sample: "N1") — kept short here too.
        String slid = "A" + uniqueSuffix.substring(0, Math.min(6, uniqueSuffix.length()));

        List<UdfValueEntry> udfValues = StepExecutor.call("Get UDF Metadata for Sub Ledger Others",
                () -> UdfValueBuilder.fetchAndBuild(this, "FINSL"));

        List<FinSlGlSiteInfo> finSlGlSites = (targetSiteCode != null)
                ? List.of(FinSlGlSiteInfo.builder().siteCode(targetSiteCode).build())
                : List.of();

        FinSlGlEntry glEntry = FinSlGlEntry.builder()
                .glcode(targetGlcode)
                .glname(targetGlname)
                .finSLGLSites(finSlGlSites)
                .build();

        SubLedgerOthersCreateRequest request = SubLedgerOthersCreateRequest.builder()
                .slname(slname)
                .slid(slid)
                .clscode(clscode)
                // See SubLedgerOthersCreateRequest's class Javadoc "open point" — reusing the
                // target GL here too, since no separate "default parent GL" lookup is captured.
                .glcode(targetGlcode)
                .finSLGLs(List.of(glEntry))
                .udfDetails(udfValues)
                .build();

        ReportLogger.dataBackfill(
                "[SubLedgerOthersApiClient] Creating Sub Ledger Others — slname='{}', slid='{}', clscode={}, targetGlcode={}, targetSiteCode={}",
                slname, slid, clscode, targetGlcode, targetSiteCode);

        SimpleResultResponse response = StepExecutor.call("Create Sub Ledger Others via API",
                () -> createFinanceSubLedgerOther(request));
        return SubLedgerOthersCreationOutcome.builder().request(request).response(response).build();
    }
}
