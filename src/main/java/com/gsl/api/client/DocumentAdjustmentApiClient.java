package com.gsl.api.client;

import com.gsl.api.core.ApiClient;
import com.gsl.api.model.documentadjustment.DocumentAdjustmentUntagRequest;
import com.gsl.api.model.documentadjustment.DocumentAdjustmentUntagResponse;
import com.gsl.config.ConfigFactory;
import io.restassured.specification.RequestSpecification;

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

    /** Prefixes {@code path} with {@code financeApiBaseUri}, matching every other Finance-module voucher client. */
    private static String url(String path) {
        return ConfigFactory.getApiConfig().financeApiBaseUri() + path;
    }

    /**
     * POST {@code Finance/api/documentAdjustments} — removes ALL {@code fintag} rows on
     * {@code request}'s base document in one call (empty {@code AdjustedDocuments}). Callers
     * build {@code request} via {@link DocumentAdjustmentUntagRequest#builder()}; the base
     * document's own {@code vchcode}/{@code scheme_docno}/actual-amount/{@code slcode} — never
     * the counter-document's — see that class's Javadoc.
     */
    public DocumentAdjustmentUntagResponse untagAllAdjustments(DocumentAdjustmentUntagRequest request) {
        return post(url("/documentAdjustments"), request, DocumentAdjustmentUntagResponse.class);
    }
}
