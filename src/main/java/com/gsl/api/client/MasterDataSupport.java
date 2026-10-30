package com.gsl.api.client;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * The mechanics every WebAPI master-data client (vendor, customer, ...) shares:  the lookup request bodies, and polling until a just-created master shows up (the create calls return no
 * code). One home, so a new master client never copies them. See docs/conventions/no-duplication.md.
 */
public final class MasterDataSupport {

    private MasterDataSupport() {}

    /** The plain paging parameters the vendor-side {@code Shared/*Lookup} calls take. */
    public static Map<String, Object> simpleLookupPaging() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("searchText", "");
        params.put("page", 1);
        params.put("start", 0);
        params.put("limit", 100);
        return params;
    }

    /**
     * The POST body of a {@code Shared/*Lookup} call exactly as the UI sends it (first page of 100). {@code withExtraQuery=false} drops the
     * {@code extraQuery} key, which {@code AdmGSTCategory/GetList} does not take.
     */
    public static Map<String, Object> lookupBody(int connectedSite, int connectedOuCode, String workDate, List<String> queryFields,
                                                  boolean withExtraQuery) {
        Map<String, Object> body = new LinkedHashMap<>();
        if (withExtraQuery) body.put("extraQuery", null);
        body.put("queryFieldName", queryFields);
        body.put("connectedSite", String.valueOf(connectedSite));
        body.put("workDate", workDate);
        body.put("connectedSiteOUCode", connectedOuCode);
        body.put("connectedSiteType", "OS-OO-CM");
        body.put("page", 1);
        body.put("start", 0);
        body.put("limit", 100);
        return body;
    }

    /** Calls {@code attempt} every {@code intervalMs} until it returns non-null or {@code timeoutMs} passes; returns null on timeout. */
    public static <T> T pollFor(long timeoutMs, long intervalMs, Supplier<T> attempt) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (true) {
            T value = attempt.get();
            if (value != null) return value;
            if (System.currentTimeMillis() >= deadline) return null;
            try {
                Thread.sleep(intervalMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            }
        }
    }
}
