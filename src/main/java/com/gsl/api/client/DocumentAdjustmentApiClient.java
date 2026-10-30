package com.gsl.api.client;

import com.gsl.api.core.ApiClient;
import com.gsl.api.core.QueryParams;
import com.gsl.api.webapi.WebApiEndpoints;
import com.gsl.api.webapi.model.request.voucher.PendingDocumentQuery;
import com.gsl.api.webapi.model.request.voucher.ReferenceSitePendingQuery;
import com.gsl.api.model.documentadjustment.DocumentAdjustmentUntagRequest;
import com.gsl.api.model.documentadjustment.DocumentAdjustmentUntagResponse;
import com.gsl.api.model.documentadjustment.PendingDocument;
import com.gsl.api.model.documentadjustment.ReferenceSitePending;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Finance module API client for the Document Adjustment flow's "untag" capability — confirmed
 * via a live HAR capture (per the user's supplied capture, against a document that had exactly
 * one tagged counter-document) as {@code POST Finance/api/documentAdjustments} with an empty
 * {@code AdjustedDocuments} list, which clears <b>every</b> {@code fintag} row on the base
 * document in one call (also confirmed via a second HAR against a multi-invoice-tagged document —
 * one call, not one call per tag row).
 *
 * <p>Generic across AR/AP/GV — the underlying {@code fintag}/{@code documentAdjustments}
 * mechanism is the same regardless of which voucher domain the tagged document belongs to (see
 * {@code DocumentAdjustmentFlow}'s Javadoc), so this client lives in the shared
 * {@code com.gsl.api.client} package rather than under any one domain's own client. Mirrors
 * {@code ApVoucherApiClient}'s {@code url()}/{@code post()} idiom exactly.
 *
 * <p><b>Only the untag direction is captured</b> — no HAR exists for the opposite "create + tag a
 * document against another" flow, so this client deliberately has no such method; see
 * {@code DbApVoucherRevertDataSource}'s {@code TAGGED}-cell backfill Javadoc for how that gap is
 * handled (an honest {@link UnsupportedOperationException}, not a guess).
 */
public class DocumentAdjustmentApiClient extends ApiClient {

    /** Confirmed literal for both AR Voucher and AP Voucher documents — see
     *  {@link DocumentAdjustmentUntagRequest}'s class Javadoc for why this is a plain constant,
     *  not an enum. */
    public static final String BASE_DOCUMENT_TYPE_VOUCHER = "VDP";

    /** Wraps an already-authenticated session {@code spec} — see {@link ApiClient}'s class Javadoc. */
    public DocumentAdjustmentApiClient(RequestSpecification spec) {
        super(spec);
    }

    /**
     * POST {@code Finance/api/documentAdjustments} — removes ALL {@code fintag} rows on
     * {@code request}'s base document in one call (empty {@code AdjustedDocuments}). Callers
     * build {@code request} via {@link DocumentAdjustmentUntagRequest#builder()}; the base
     * document's own {@code vchcode}/{@code scheme_docno}/actual-amount/{@code slcode} — never
     * the counter-document's — see that class's Javadoc.
     */
    public DocumentAdjustmentUntagResponse untagAllAdjustments(DocumentAdjustmentUntagRequest request) {
        return post(WebApiEndpoints.DocumentAdjustment.UNTAG, request, DocumentAdjustmentUntagResponse.class);
    }

    /** {@code module} value (and path segment of the voucher list) for AP Voucher. */
    public static final String MODULE_AP = "apvoucher";
    /** {@code module} value (and path segment of the voucher list) for AR Voucher. */
    public static final String MODULE_AR = "arvoucher";

    /**
     * GET {@code Finance/api/DocumentAdjustments/{apvoucher|arvoucher}} — every document with a pending balance for one sub-ledger
     * (the Select Document popup's list), paged until exhausted. Replaces the {@code v_pending_adj} document query.
     *
     * @param module {@link #MODULE_AP} or {@link #MODULE_AR}
     * @param date   voucher date, {@code yyyy-MM-ddT00:00:00}
     */
    public List<PendingDocument> fetchPendingDocuments(String module, long glCode, long slCode, int ouCode, String date) {
        PendingDocumentQuery query = new PendingDocumentQuery(glCode, slCode, ouCode, date, BASE_DOCUMENT_TYPE_VOUCHER, date);
        List<PendingDocument> all = new ArrayList<>();
        int page = 1;
        while (true) {
            Map<String, Object> params = QueryParams.toMap(query);
            params.put("page", page);
            params.put("start", (page - 1) * PENDING_PAGE_SIZE);
            params.put("limit", PENDING_PAGE_SIZE);
            Response response = getRaw(WebApiEndpoints.DocumentAdjustment.pending(module), params);
            List<PendingDocument> items = response.jsonPath().getList("result.items", PendingDocument.class);
            if (items == null || items.isEmpty()) break;
            all.addAll(items);
            Integer total = response.jsonPath().getInt("result.totalRecords");
            if (total == null || all.size() >= total || items.size() < PENDING_PAGE_SIZE) break;
            page++;
        }
        return all;
    }

    /**
     * GET {@code Finance/api/DocumentAdjustments/{ap|ar}/reference?target=referencesiteforfifo} — pending amount per reference site
     * for one sub-ledger. Replaces {@code ReferenceSiteQuery.findBestRefSiteForSl}.
     */
    public List<ReferenceSitePending> fetchReferenceSitePending(String module, long glCode, long slCode, int ouCode, String date) {
        ReferenceSitePendingQuery query = ReferenceSitePendingQuery.builder().module(module).ouCode(ouCode).slCode(slCode).glCode(glCode)
                .connectedOuCode(ouCode).date(date).workDate(date).build();
        String segment = MODULE_AP.equals(module) ? "ap" : "ar";
        Response response = getRawQuery(WebApiEndpoints.DocumentAdjustment.reference(segment), query);
        List<ReferenceSitePending> items = response.jsonPath().getList("result", ReferenceSitePending.class);
        return items == null ? List.of() : items;
    }

    private static final int PENDING_PAGE_SIZE = 500;
}
