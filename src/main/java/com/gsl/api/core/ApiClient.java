package com.gsl.api.core;

import com.gsl.reports.ApiCallReportLogger;
import com.gsl.reports.ReportLogger;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;

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
        return RestAssured.given().spec(spec);
    }

    protected <T> T get(String endpoint, Class<T> responseType) {
        long start = System.currentTimeMillis();
        Response response = fresh().get(endpoint);
        ApiCallReportLogger.logCall("GET", endpoint, null, response, System.currentTimeMillis() - start);
        validateResponse(response, endpoint);
        return response.as(responseType);
    }

    protected <T> T post(String endpoint, Object body, Class<T> responseType) {
        long start = System.currentTimeMillis();
        Response response = fresh().body(body).post(endpoint);
        ApiCallReportLogger.logCall("POST", endpoint, body, response, System.currentTimeMillis() - start);
        validateResponse(response, endpoint);
        return response.as(responseType);
    }

    protected Response postRaw(String endpoint, Object body) {
        long start = System.currentTimeMillis();
        Response response = fresh().body(body).post(endpoint);
        ApiCallReportLogger.logCall("POST", endpoint, body, response, System.currentTimeMillis() - start);
        validateResponse(response, endpoint);
        return response;
    }

    protected Response postRaw(String endpoint, Object body, Map<String, Object> queryParams) {
        long start = System.currentTimeMillis();
        Response response = fresh().queryParams(queryParams).body(body).post(endpoint);
        ApiCallReportLogger.logCall("POST", endpoint, body, response, System.currentTimeMillis() - start);
        validateResponse(response, endpoint);
        return response;
    }

    protected Response getRaw(String endpoint) {
        long start = System.currentTimeMillis();
        Response response = fresh().get(endpoint);
        ApiCallReportLogger.logCall("GET", endpoint, null, response, System.currentTimeMillis() - start);
        validateResponse(response, endpoint);
        return response;
    }

    protected Response getRaw(String endpoint, Map<String, Object> queryParams) {
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

    protected void validateResponse(Response response, String endpoint) {
        int status = response.getStatusCode();
        if (status >= 400) {
            ReportLogger.networkError("[ApiClient] {} -> HTTP {}: {}", endpoint, status, response.getBody().asString());
            throw new ApiException("Call to " + endpoint + " failed with HTTP " + status, status);
        }
        Boolean success = response.jsonPath().get("success");
        if (Boolean.FALSE.equals(success)) {
            String error = response.jsonPath().getString("error");
            ReportLogger.networkError("[ApiClient] {} -> success=false, error={}", endpoint, error);
            throw new ApiException("Call to " + endpoint + " returned success=false: " + error, status);
        }
    }
}
