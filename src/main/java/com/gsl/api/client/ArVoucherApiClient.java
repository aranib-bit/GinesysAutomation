package com.gsl.api.client;

import com.gsl.api.core.ApiException;
import com.gsl.api.webapi.WebApiEndpoints;
import com.gsl.api.model.arvoucher.ArVoucherCreateRequest;
import com.gsl.api.model.arvoucher.ArVoucherCreateResponse;
import com.gsl.api.model.arvoucher.ArVoucherDetailLine;
import com.gsl.api.model.common.CashBankLedgerLookupResult;
import com.gsl.api.model.common.DocNumberSchemeEntry;
import com.gsl.api.model.common.ReferenceSiteLookupResult;
import com.gsl.api.model.common.SubLedgerLookupResult;
import com.gsl.api.model.udf.UdfValueEntry;
import com.gsl.reports.ReportLogger;
import com.gsl.reports.StepExecutor;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Finance module API client for AR Voucher creation. Mirrors the real flow captured in
 * {@code CreateARVoucherstage-ideal.ginesys.cloud4.har} — used both as a standalone document
 * type AND as the AP Voucher document-payment backfill mechanism for any {@code clsCode} whose
 * {@code clstype} isn't Supplier (Service Invoice Adhoc's GetVendors lookup is Supplier-only, so
 * it can never satisfy a Customer/Jobber/Others/etc. shortfall — see
 * {@code ArVoucherApBackfillStrategy}). An AR Voucher against any sub-ledger classification
 * lands in {@code v_pending_adj} with {@code debit_credit='C'} once posted — exactly what
 * {@code DbApVoucherDataSource.selectApDocumentsAtomic()} is searching for.
 *
 * <p>Endpoints live under the Finance module's own app path ({@code financeApiBaseUri}), not the
 * WebAPI path the other four flow clients use — confirmed via the HAR that the same authenticated
 * WEBAPI session's {@code ginesysauth} bearer header works unchanged against both, only the base
 * path differs, so every call here passes a full absolute URL to override the shared spec's own
 * baseURI rather than needing a separate login.
 */
public class ArVoucherApiClient extends FinanceVoucherLookupClient {


    /** Wraps an already-authenticated session {@code spec} — see {@link ApiClient}'s class Javadoc. */
    public ArVoucherApiClient(RequestSpecification spec) {
        super(spec);
    }

    @Override protected String module() { return "arvoucher"; }
    @Override protected String menuShortCode() { return "FinanceARVoucher"; }
    @Override protected String voucherSegment() { return "ar"; }
    @Override protected String rootviewSegment() { return "arvouchers"; }

    /**
     * Resolves a live sub-ledger matching {@code requiredClsCode} (0 = no filter, first result
     * wins). Unlike {@code ServiceInvoiceAdhocApiClient.resolveVendor}, there is no silent
     * fallback to a differently-classified SL on miss — that exact shortcut was the root cause of
     * a prior AP Voucher backfill bug (a wrongly-classified document that never satisfied the
     * shortfall it was meant to fix). A genuine miss here means this environment has no active
     * sub-ledger of the required classification at all, which callers must see as a hard failure.
     */
    public SubLedgerLookupResult resolveSubLedger(
            int connectedSite, int connectedOUCode, int connectedUserCode, String workDate, long requiredClsCode) {
        return resolveSubLedger(connectedSite, connectedOUCode, connectedUserCode, workDate, requiredClsCode, sl -> true, "usable (live)");
    }

    /** POST Finance/api/vouchers/ar — the final save call. */
    public ArVoucherCreateResponse createArVoucher(ArVoucherCreateRequest request) {
        return post(WebApiEndpoints.Voucher.root("ar"), request, ArVoucherCreateResponse.class);
    }

    /** {@code PUT /vouchers/ar} - edit an Unposted AR Voucher ({@code request} carries {@code code}, {@code docNumber}, {@code dataVersion} and the line codes/versions). */
    public ArVoucherCreateResponse updateArVoucher(ArVoucherCreateRequest request) {
        return put(WebApiEndpoints.Voucher.root("ar"), request, ArVoucherCreateResponse.class);
    }

    /**
     * Negative-scenario counterpart to {@link #createArVoucher(ArVoucherCreateRequest)} — for a
     * deliberately incomplete/invalid {@code request}, returns the raw {@link Response} instead
     * of throwing.
     */
    public Response createArVoucherExpectFailure(ArVoucherCreateRequest request) {
        return postRawExpectFailure(endpointUrl(WebApiEndpoints.Voucher.root("ar")), request);
    }
}
