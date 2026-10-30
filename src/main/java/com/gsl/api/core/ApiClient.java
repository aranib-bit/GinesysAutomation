package com.gsl.api.core;

import com.gsl.config.ConfigFactory;
import com.gsl.reports.ApiCallReportLogger;
import com.gsl.reports.ReportLogger;
import io.restassured.RestAssured;
import io.restassured.config.JsonConfig;
import io.restassured.config.RestAssuredConfig;
import io.restassured.path.json.config.JsonPathConfig;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Base class for all flow-specific API clients (ItemApiClient, GrcApiClient, DcApiClient,
 * TransferOutApiClient, ServiceInvoiceAdhocApiClient, ArVoucherApiClient). Wraps a
 * pre-authenticated {@link RequestSpecification} built by {@code ApiSessionFactory} — the client
 * itself never touches auth headers or the base URI, it only knows endpoints and payload shapes.
 *
 * <p>Every call is logged via {@link ApiCallReportLogger} (URL/payload/response + duration) —
 * one instrumentation point here covers all six flow clients' ~30+ endpoint methods. Payloads
 * and responses here are business data (item names, prices, vendor codes) never credentials, so
 * full-body logging is safe.
 */
public abstract class ApiClient {

    protected final RequestSpecification spec;

    /** @param spec the pre-authenticated spec built by {@code ApiSessionFactory} for this client's session. */
    protected ApiClient(RequestSpecification spec) {
        this.spec = spec;
    }

    /**
     * Derives a fresh, disposable request from the shared {@code spec} for one call — never
     * call HTTP verbs directly on {@code spec} itself. RestAssured's {@code queryParams(Map)}/
     * {@code body(Object)} mutate the spec instance they're called on, and every method here
     * shares the SAME {@code spec} across the client's entire lifetime (and across every other
     * flow client in the same {@code ApiSessionBundle}) — calling them on {@code spec} directly
     * leaks query params from one call into every subsequent call for the rest of the session.
     * Confirmed live: a GET picked up a stale {@code queryFieldName} value set by an earlier,
     * unrelated GET and the server rejected it (500, "No property or field 'glName' exists in
     * type 'SiteView'") — {@code ArVoucherApiClient} was the first consumer to chain multiple
     * GET calls reusing the same query-param key, which is what exposed this.
     * {@code given().spec(spec)} inherits auth headers/base URI but is its own throwaway builder.
     */
    private RequestSpecification fresh() {
        // JSON numbers are read as BigDecimal: the default (float) turns 667157.99 into 667158.0, which silently corrupts every amount a client reads.
        return RestAssured.given().spec(spec).config(RestAssuredConfig.config()
                .jsonConfig(JsonConfig.jsonConfig().numberReturnType(JsonPathConfig.NumberReturnType.BIG_DECIMAL)));
    }

    /** Safety cap for {@link #getAllPages}. */
    private static final int MAX_LOOKUP_PAGES = 50;

    /** The Finance API base ({@code financeApiBaseUri}, i.e. {@code .../Finance/api}); overridable so unit tests can point at a local server. */
    protected String financeBase() {
        return ConfigFactory.getApiConfig().financeApiBaseUri();
    }

    /** Prefixes {@code path} with {@link #financeBase()} - the one place a Finance endpoint URL is built. */
    protected String financeUrl(String path) {
        return financeBase() + path;
    }

    /** The URL to call for {@code endpoint}: the path itself for {@link Host#WEB_API} (the session spec carries that base), the Finance base + path otherwise. */
    protected String endpointUrl(Endpoint endpoint) {
        return endpoint.host() == Host.FINANCE_API ? financeUrl(endpoint.path()) : endpoint.path();
    }

    /** {@link #post(String, Object, Class)} for a registered {@link Endpoint}. */
    protected <T> T post(Endpoint endpoint, Object body, Class<T> responseType) {
        return post(endpointUrl(endpoint), body, responseType);
    }

    /** {@link #put(String, Object, Class)} for a registered {@link Endpoint}. */
    protected <T> T put(Endpoint endpoint, Object body, Class<T> responseType) {
        return put(endpointUrl(endpoint), body, responseType);
    }

    /** {@link #delete(String, Object, Class)} for a registered {@link Endpoint}. */
    protected <T> T delete(Endpoint endpoint, Object body, Class<T> responseType) {
        return delete(endpointUrl(endpoint), body, responseType);
    }

    /** {@link #postRaw(String, Object)} for a registered {@link Endpoint}. */
    protected Response postRaw(Endpoint endpoint, Object body) {
        return postRaw(endpointUrl(endpoint), body);
    }

    /** {@link #postRaw(String, Object, Map)} for a registered {@link Endpoint}. */
    protected Response postRaw(Endpoint endpoint, Object body, Map<String, Object> queryParams) {
        return postRaw(endpointUrl(endpoint), body, queryParams);
    }

    /** {@link #getRaw(String, Map)} for a registered {@link Endpoint}. */
    public Response getRaw(Endpoint endpoint, Map<String, Object> queryParams) {
        return getRaw(endpointUrl(endpoint), queryParams);
    }

    /** {@link #getRaw(Endpoint, Map)} with the query given as a POJO ({@link QueryParams}). */
    public Response getRawQuery(Endpoint endpoint, Object query) {
        return getRaw(endpointUrl(endpoint), QueryParams.toMap(query));
    }

    /** {@link #getRawExpectFailure(String, Map)} for a registered {@link Endpoint} with a query POJO. */
    protected Response getRawQueryExpectFailure(Endpoint endpoint, Object query) {
        return getRawExpectFailure(endpointUrl(endpoint), QueryParams.toMap(query));
    }

    /** {@link #getAllPages(String, Map, int, Class)} for a registered {@link Endpoint} with a query POJO. */
    protected <T> List<T> getAllPages(Endpoint endpoint, Object query, int pageSize, Class<T> type) {
        return getAllPages(endpointUrl(endpoint), QueryParams.toMap(query), pageSize, type);
    }

    /** {@link #get(String, Map, Class)} for a registered {@link Endpoint}. */
    protected <T> T get(Endpoint endpoint, Map<String, Object> queryParams, Class<T> responseType) {
        return get(endpointUrl(endpoint), queryParams, responseType);
    }

    /**
     * GET {@code url} read page by page ({@code page}/{@code start}/{@code limit}) until {@code result.totalCount} items are collected, a page
     * comes back empty, or a safety cap is hit. The first page is requested exactly as a single call used to be, so a server that already
     * honours {@code pagingNotRequired}/a big limit costs no extra request; a server that caps the page is followed instead of silently
     * truncated (qaprod returned 25 of 253 sub-ledgers despite {@code pagingNotRequired=true}). {@code totalCount} vs fetched is logged
     * whenever they differ. The one paging loop for every lookup/list client.
     */
    protected <T> List<T> getAllPages(String url, Map<String, Object> baseParams, int pageSize, Class<T> type) {
        List<T> all = new ArrayList<>();
        int total = Integer.MAX_VALUE;
        for (int page = 1; page <= MAX_LOOKUP_PAGES && all.size() < total; page++) {
            Map<String, Object> params = new LinkedHashMap<>(baseParams);
            params.put("page", page);
            params.put("start", all.size());
            params.put("limit", pageSize);
            Response response = getRaw(url, params);
            List<T> items = response.jsonPath().getList("result.items", type);
            Integer reported = response.jsonPath().getObject("result.totalCount", Integer.class);
            if (reported != null) total = reported;
            if (items == null || items.isEmpty()) break;
            all.addAll(items);
            if (reported == null) break; // no totalCount advertised: a single page is all we can trust
        }
        if (total != Integer.MAX_VALUE && total != all.size()) {
            ReportLogger.warn("[" + getClass().getSimpleName() + "] " + url + " totalCount=" + total + " but fetched " + all.size());
        }
        return all;
    }

    /** The response body read as {@code type} whatever its Content-Type says (a response POJO, never a {@code jsonPath()} navigation). */
    protected static <T> T bodyAs(Response response, Class<T> type) {
        return response.getBody().as(type, io.restassured.mapper.ObjectMapperType.JACKSON_2);
    }

    /** GET {@code endpoint}, validate the response, and deserialize {@code result} into {@code responseType}. */
    protected <T> T get(String endpoint, Class<T> responseType) {
        long start = System.currentTimeMillis();
        Response response = fresh().get(endpoint);
        ApiCallReportLogger.logCall("GET", endpoint, null, response, System.currentTimeMillis() - start);
        validateResponse(response, endpoint);
        return response.as(responseType);
    }

    /** POST {@code body} to {@code endpoint}, validate the response, and deserialize {@code result} into {@code responseType}. */
    protected <T> T post(String endpoint, Object body, Class<T> responseType) {
        long start = System.currentTimeMillis();
        Response response = fresh().body(body).post(endpoint);
        ApiCallReportLogger.logCall("POST", endpoint, body, response, System.currentTimeMillis() - start);
        validateResponse(response, endpoint);
        return response.as(responseType);
    }

    /**
     * PUT {@code body} to {@code endpoint}, validate the response, and deserialize {@code result}
     * into {@code responseType} — the PUT counterpart to {@link #post(String, Object, Class)},
     * added for {@code ApVoucherApiClient.revertApVoucher} (confirmed via HAR as a real
     * {@code PUT Finance/api/vouchers/ap/revert} call — no other flow client in this codebase has
     * needed a PUT verb before this one). Mirrors {@code post()}'s shape exactly, including the
     * same {@code fresh()}-derived disposable request (see that method's Javadoc for why calling
     * an HTTP verb directly on the shared {@code spec} would leak state across calls) and the same
     * {@link ApiCallReportLogger} instrumentation.
     */
    protected <T> T put(String endpoint, Object body, Class<T> responseType) {
        long start = System.currentTimeMillis();
        Response response = fresh().body(body).put(endpoint);
        ApiCallReportLogger.logCall("PUT", endpoint, body, response, System.currentTimeMillis() - start);
        validateResponse(response, endpoint);
        return response.as(responseType);
    }

    /** GET {@code endpoint} with query params, validate the response, and deserialize it into {@code responseType}. */
    protected <T> T get(String endpoint, Map<String, Object> queryParams, Class<T> responseType) {
        return getRaw(endpoint, queryParams).as(responseType);
    }

    /** PUT {@code body} to {@code endpoint}, validate the response, and return the raw {@link Response} (no deserialization). */
    protected Response putRaw(String endpoint, Object body) {
        long start = System.currentTimeMillis();
        Response response = fresh().body(body).put(endpoint);
        ApiCallReportLogger.logCall("PUT", endpoint, body, response, System.currentTimeMillis() - start);
        validateResponse(response, endpoint);
        return response;
    }

    /** {@link #putRaw(String, Object)} with query params attached. */
    protected Response putRaw(String endpoint, Object body, Map<String, Object> queryParams) {
        long start = System.currentTimeMillis();
        Response response = fresh().queryParams(queryParams).body(body).put(endpoint);
        ApiCallReportLogger.logCall("PUT", endpoint, body, response, System.currentTimeMillis() - start);
        validateResponse(response, endpoint);
        return response;
    }

    /** {@link #getRawExpectFailure(String)} counterpart for PUT — see that method's javadoc. */
    protected Response putRawExpectFailure(String endpoint, Object body) {
        long start = System.currentTimeMillis();
        Response response = fresh().body(body).put(endpoint);
        ApiCallReportLogger.logCall("PUT", endpoint, body, response, System.currentTimeMillis() - start);
        return response;
    }

    /**
     * DELETE with a JSON body (the Finance voucher delete endpoints take
     * {@code {"empCode":..,"connectedOUCode":..,"voucherCodes":[..]}}), validate the response and
     * deserialize it into {@code responseType}. Same {@code fresh()} request and
     * {@link ApiCallReportLogger} instrumentation as the other verbs.
     */
    protected <T> T delete(String endpoint, Object body, Class<T> responseType) {
        return deleteRaw(endpoint, body).as(responseType);
    }

    /** DELETE {@code body} to {@code endpoint}, validate the response, and return the raw {@link Response}. */
    protected Response deleteRaw(String endpoint, Object body) {
        long start = System.currentTimeMillis();
        Response response = fresh().body(body).delete(endpoint);
        ApiCallReportLogger.logCall("DELETE", endpoint, body, response, System.currentTimeMillis() - start);
        validateResponse(response, endpoint);
        return response;
    }

    /** {@link #getRawExpectFailure(String)} counterpart for DELETE — see that method's javadoc. */
    protected Response deleteRawExpectFailure(String endpoint, Object body) {
        long start = System.currentTimeMillis();
        Response response = fresh().body(body).delete(endpoint);
        ApiCallReportLogger.logCall("DELETE", endpoint, body, response, System.currentTimeMillis() - start);
        return response;
    }

    /** POST {@code body} to {@code endpoint}, validate the response, and return the raw {@link Response} (no deserialization). */
    protected Response postRaw(String endpoint, Object body) {
        long start = System.currentTimeMillis();
        Response response = fresh().body(body).post(endpoint);
        ApiCallReportLogger.logCall("POST", endpoint, body, response, System.currentTimeMillis() - start);
        validateResponse(response, endpoint);
        return response;
    }

    /** {@link #postRaw(String, Object)} with query params attached. */
    protected Response postRaw(String endpoint, Object body, Map<String, Object> queryParams) {
        long start = System.currentTimeMillis();
        Response response = fresh().queryParams(queryParams).body(body).post(endpoint);
        ApiCallReportLogger.logCall("POST", endpoint, body, response, System.currentTimeMillis() - start);
        validateResponse(response, endpoint);
        return response;
    }

    /** GET {@code endpoint}, validate the response, and return the raw {@link Response} (no deserialization). */
    protected Response getRaw(String endpoint) {
        long start = System.currentTimeMillis();
        Response response = fresh().get(endpoint);
        ApiCallReportLogger.logCall("GET", endpoint, null, response, System.currentTimeMillis() - start);
        validateResponse(response, endpoint);
        return response;
    }

    /**
     * Public (widened from {@code protected}) so {@code UdfValueBuilder} — a standalone helper in
     * a different package, not an {@code ApiClient} subclass — can drive the shared
     * {@code GET /udf/GetUDFMetaData} call on behalf of any flow client that owns a session spec.
     */
    public Response getRaw(String endpoint, Map<String, Object> queryParams) {
        long start = System.currentTimeMillis();
        Response response = fresh().queryParams(queryParams).get(endpoint);
        ApiCallReportLogger.logCall("GET", endpoint, queryParams, response, System.currentTimeMillis() - start);
        validateResponse(response, endpoint);
        return response;
    }

    /**
     * Negative-scenario counterpart to {@link #getRaw(String)} — same {@code fresh()}-derived
     * request and {@link ApiCallReportLogger} instrumentation, but deliberately skips
     * {@link #validateResponse(Response, String)}'s throw so a call expected to fail (bad
     * input, missing auth, invalid id, …) returns its raw {@link Response} for the caller to
     * assert on (status code, {@code success} flag, error message) instead of blowing up the
     * test. Never use this for calls expected to succeed — a genuine unexpected failure here
     * is silently swallowed rather than surfaced as a thrown {@link ApiException}.
     */
    protected Response getRawExpectFailure(String endpoint) {
        long start = System.currentTimeMillis();
        Response response = fresh().get(endpoint);
        ApiCallReportLogger.logCall("GET", endpoint, null, response, System.currentTimeMillis() - start);
        return response;
    }

    /** {@link #getRawExpectFailure(String)} with query params — see that method's javadoc. */
    protected Response getRawExpectFailure(String endpoint, Map<String, Object> queryParams) {
        long start = System.currentTimeMillis();
        Response response = fresh().queryParams(queryParams).get(endpoint);
        ApiCallReportLogger.logCall("GET", endpoint, queryParams, response, System.currentTimeMillis() - start);
        return response;
    }

    /** {@link #getRawExpectFailure(String)} counterpart for POST — see that method's javadoc. */
    protected Response postRawExpectFailure(String endpoint, Object body) {
        long start = System.currentTimeMillis();
        Response response = fresh().body(body).post(endpoint);
        ApiCallReportLogger.logCall("POST", endpoint, body, response, System.currentTimeMillis() - start);
        return response;
    }

    /** {@link #getRawExpectFailure(String)} counterpart for POST with query params. */
    protected Response postRawExpectFailure(String endpoint, Object body, Map<String, Object> queryParams) {
        long start = System.currentTimeMillis();
        Response response = fresh().queryParams(queryParams).body(body).post(endpoint);
        ApiCallReportLogger.logCall("POST", endpoint, body, response, System.currentTimeMillis() - start);
        return response;
    }

    /** Max response-body characters carried into an {@link ApiException} message. */
    private static final int ERROR_BODY_MAX_CHARS = 500;

    private static String truncate(String text, int max) {
        String oneLine = text.replaceAll("\\s+", " ").trim();
        return oneLine.length() <= max ? oneLine : oneLine.substring(0, max) + "…";
    }

    /** Throws {@link ApiException} on an HTTP 4xx/5xx status or an envelope with {@code success=false}; otherwise a no-op. */
    protected void validateResponse(Response response, String endpoint) {
        int status = response.getStatusCode();
        if (status >= 400) {
            String body = response.getBody().asString();
            ReportLogger.networkError("[ApiClient] {} -> HTTP {}: {}", endpoint, status, body);
            // Body goes into the message too (truncated) so the real server reason — e.g. a doc
            // scheme with no numbers left — is visible wherever the exception is reported.
            throw new ApiException("Call to " + endpoint + " failed with HTTP " + status
                    + (body == null || body.isBlank() ? "" : ": " + truncate(body, ERROR_BODY_MAX_CHARS)), status);
        }
        Boolean success = response.jsonPath().get("success");
        if (Boolean.FALSE.equals(success)) {
            String error = response.jsonPath().getString("error");
            ReportLogger.networkError("[ApiClient] {} -> success=false, error={}", endpoint, error);
            throw new ApiException("Call to " + endpoint + " returned success=false: " + error, status);
        }
    }
}
