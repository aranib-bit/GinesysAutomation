package com.gsl.api.client;

import com.gsl.api.core.ApiClient;
import com.gsl.api.core.ApiException;
import com.gsl.api.model.common.SimpleResultResponse;
import com.gsl.api.model.ledger.AccountGroupTreeNode;
import com.gsl.api.model.ledger.LedgerCreateRequest;
import com.gsl.api.model.ledger.LedgerCreationOutcome;
import com.gsl.api.model.ledger.LedgerGroupRef;
import com.gsl.api.model.ledger.LedgerSiteInfo;
import com.gsl.api.model.ledger.SiteLookupResult;
import com.gsl.database.DatabaseManager;
import com.gsl.database.dao.finance.FinAccGrpDao;
import com.gsl.enums.GlAccountType;
import com.gsl.reports.ReportLogger;
import com.gsl.reports.StepExecutor;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import org.jdbi.v3.core.Jdbi;

import java.time.LocalDate;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Finance module API client for General Ledger (GL) master creation — mirrors the real flow
 * captured in {@code CreateExpenseNotSiteApplicable...har}/{@code CreateExpenseSiteApplicable...har}.
 * Backs {@code LedgerMasterBackfillStrategy}, General Voucher's {@code fingl} shortfall backfill
 * (see the plan's Work Item 2) — this client only ever creates {@code srctype='G'} (general)
 * Expense/Income/Asset/Liability ledgers, never Cash/Bank, which is a different, un-captured
 * payload shape and stays out of scope.
 *
 * <p>All endpoints here live under the same {@code webApiBaseUri}{@code webApiApiPrefix} base as
 * every other flow client's relative-path calls (confirmed via HAR paths all being
 * {@code /WebAPI/api/...}) — none of them use {@code financeApiBaseUri} the way
 * {@link ArVoucherApiClient} does for its own {@code Finance/api/...} endpoints.
 */
public class LedgerApiClient extends ApiClient {

    public LedgerApiClient(RequestSpecification spec) {
        super(spec);
    }

    /**
     * GET /FinGroup/GetAccountGrpTree — the whole {@code fingrp} tree scoped to every account
     * type in one call (confirmed via HAR: {@code grpType=A,L,I,E} fetches all four branches at
     * once, exactly like the real "New Ledger" screen's group picker does). Returns the raw
     * top-level node list — {@code result} is reused as both the response envelope's array key
     * and each node's own children key (see {@link AccountGroupTreeNode}'s Javadoc).
     */
    public List<AccountGroupTreeNode> fetchAccountGroupTree() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("grpType", "A,L,I,E");
        params.put("workDate", LocalDate.now() + "T00:00:00");
        params.put("node", "root");
        Response response = getRaw("/FinGroup/GetAccountGrpTree", params);
        return response.jsonPath().getList("result", AccountGroupTreeNode.class);
    }

    /** POST /Shared/GetSitesForFinanceLookup — live site codes for {@code ledgerSiteInfo}. */
    public List<SiteLookupResult> fetchSitesForFinanceLookup() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("searchText", "");
        params.put("page", 1);
        params.put("start", 0);
        params.put("limit", 50);
        Response response = postRaw("/Shared/GetSitesForFinanceLookup", Map.of(), params);
        return response.jsonPath().getList("result.items", SiteLookupResult.class);
    }

    /** POST /Ledger/Create?code=0 — the final save call. */
    public SimpleResultResponse createLedger(LedgerCreateRequest request) {
        return post("/Ledger/Create", request, SimpleResultResponse.class);
    }

    /**
     * Resolves a leaf {@code fingrp} node's {@code grpCode} for the given account type —
     * "leaf" is confirmed authoritatively via {@link FinAccGrpDao#countChildren(long)} against
     * the live DB (the API tree's own {@code result.isEmpty()} shape is only used as a cheap
     * first-pass filter), per the user-confirmed business rule: a group with any child row
     * (e.g. {@code fingrp} code 10 with rows where {@code parcode=10}) is restricted and must
     * never be sent as {@code grpcode} — the ledger would either fail to save or attach to a
     * non-postable node.
     *
     * @throws ApiException if no leaf group exists for {@code type} — creating new {@code fingrp}
     *                       nodes is out of scope for this backfill (no HAR evidence for that endpoint).
     */
    public long resolveLeafGroupCode(GlAccountType type) {
        List<AccountGroupTreeNode> tree = fetchAccountGroupTree();
        Jdbi jdbi = DatabaseManager.getJdbi();
        FinAccGrpDao finAccGrpDao = jdbi.onDemand(FinAccGrpDao.class);

        for (AccountGroupTreeNode root : tree) {
            for (AccountGroupTreeNode node : root.flatten()) {
                if (!type.code().equalsIgnoreCase(node.getType())) continue;
                if (!node.isLeafByTree()) continue; // cheap first-pass filter
                if (finAccGrpDao.countChildren(node.getGrpCode()) > 0) {
                    ReportLogger.dataBackfillWarn(
                            "[LedgerApiClient] Group '{}' (grpCode={}) looked like a leaf in the API tree "
                                    + "but has child row(s) in fingrp — skipping, restricted per the leaf-node rule.",
                            node.getName(), node.getGrpCode());
                    continue;
                }
                ReportLogger.dataBackfill("[LedgerApiClient] Resolved leaf group '{}' (grpCode={}) for type={}",
                        node.getName(), node.getGrpCode(), type);
                return node.getGrpCode();
            }
        }
        throw new ApiException("No leaf fingrp group found for account type=" + type
                + " — cannot create a ledger without a valid, childless Finance Group to attach it to. "
                + "Creating new fingrp nodes is out of scope for this backfill.", 0);
    }

    /**
     * Orchestrates the full flow for a given (type, Sub-Ledger-Applicable, Site-Applicable)
     * combination — same "thread each live response into the next, never hardcode" principle as
     * {@code ArVoucherApiClient.createArVoucherWithDefaults}.
     *
     * @param type                     Expense/Income/Asset/Liability (fingl.type)
     * @param subLedgerApplicable      fingl.slapp — "Y"/"N"
     * @param requestedSiteApplicable  fingl.costapp — "Y"/"N"; whether {@code ledgerSiteInfo} ends up populated
     * @param assignedSiteCodes        the calling test's logged-in user's assigned sites
     *                                 ({@code admsite.code} values, resolved live via
     *                                 {@code DbGeneralVoucherDataSource.fetchAssignedSiteCodesLive}
     *                                 — an already-resolved plain value, never fetched by this
     *                                 class). The new ledger's {@code ledgerSiteInfo} must carry a
     *                                 site from this collection when {@code requestedSiteApplicable}
     *                                 is true — any one of them is fine, but attaching a site
     *                                 outside this set (e.g. an arbitrary "first result" from the
     *                                 lookup) is exactly the bug this parameter exists to prevent —
     *                                 see run 20260810_135033's "No site is assigned to this
     *                                 ledger" root-cause.
     * @throws IllegalArgumentException if {@code type} is EXPENSE/INCOME and {@code requestedSiteApplicable}
     *                                   is false — rejected per the confirmed business rule, never auto-corrected.
     */
    public LedgerCreationOutcome createLedgerWithDefaults(
            GlAccountType type, boolean subLedgerApplicable, boolean requestedSiteApplicable,
            Collection<Long> assignedSiteCodes) {

        if ((type == GlAccountType.EXPENSE || type == GlAccountType.INCOME) && !requestedSiteApplicable) {
            throw new IllegalArgumentException("Invalid combination: " + type
                    + " ledgers must be Site applicable (Sub Ledger applicable can still be Yes/No) — "
                    + "requested Site NOT applicable, which is not a valid combination per the confirmed business rule.");
        }

        long grpCode = StepExecutor.call("Resolve leaf Finance Group for Ledger",
                () -> resolveLeafGroupCode(type));

        List<LedgerSiteInfo> ledgerSiteInfo = List.of();
        if (requestedSiteApplicable) {
            List<SiteLookupResult> sites = StepExecutor.call("Get Sites for Ledger", this::fetchSitesForFinanceLookup);
            SiteLookupResult matched = sites.stream()
                    .filter(s -> assignedSiteCodes.contains(s.getCode()))
                    .findFirst()
                    .orElseThrow(() -> new ApiException("GetSitesForFinanceLookup returned none of the "
                            + "logged-in user's assigned sites " + assignedSiteCodes + " among its "
                            + sites.size() + " result(s) — refusing to attach a site outside that set to a "
                            + "new Site-applicable ledger for type=" + type + ". Attaching an arbitrary site "
                            + "here is exactly the bug that produced \"No site is assigned to this ledger\" "
                            + "in run 20260810_135033.", 0));
            ledgerSiteInfo = List.of(LedgerSiteInfo.builder().siteCode(matched.getCode()).build());
        }

        String uniqueSuffix = String.valueOf(System.nanoTime());
        String glname = "AUTO_GL_" + type.code() + "_" + uniqueSuffix;
        // Short, still-unique abbreviation — abbre columns are typically narrow, so this uses a
        // base-36 compressed nanoTime rather than the full decimal suffix used in glname.
        String abbre = "AG" + Long.toString(System.nanoTime(), 36).toUpperCase();

        LedgerCreateRequest request = LedgerCreateRequest.builder()
                .srctype("G")
                .slapp(subLedgerApplicable ? "Y" : "N")
                .costapp(requestedSiteApplicable ? "Y" : "N")
                .glname(glname)
                .abbre(abbre)
                .type(type.code())
                .grpcode(LedgerGroupRef.builder().code(String.valueOf(grpCode)).build())
                .ledgerSiteInfo(ledgerSiteInfo)
                .build();

        ReportLogger.dataBackfill(
                "[LedgerApiClient] Creating Ledger — type={}, slapp={}, costapp={}, glname='{}', grpCode={}, siteCount={}",
                type, request.getSlapp(), request.getCostapp(), glname, grpCode, ledgerSiteInfo.size());

        SimpleResultResponse response = StepExecutor.call("Create Ledger via API", () -> createLedger(request));
        return LedgerCreationOutcome.builder().request(request).response(response).build();
    }
}
