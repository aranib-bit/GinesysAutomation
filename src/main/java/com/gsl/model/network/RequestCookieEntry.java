package com.gsl.model.network;

import lombok.Builder;
import lombok.Data;

/**
 * One cookie sent on a captured request's {@code Cookie} header, enriched (when a matching
 * cookie was found via CDP's {@code getAllCookies()}) with domain/path/expiry/flag metadata that
 * the raw header alone does not carry. Built by {@code NetworkTimingCapture}.
 * Getters/setters/builder are Lombok-generated ({@code @Data @Builder}).
 */
@Data
@Builder
public class RequestCookieEntry {
    private String name;
    private String value;
    private String domain;
    private String path;
    private String expires;
    private int    size;
    private boolean httpOnly;
    private boolean secure;
    private String sameSite;
    private String priority;
}
