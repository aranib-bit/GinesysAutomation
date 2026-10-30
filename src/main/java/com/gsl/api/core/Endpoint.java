package com.gsl.api.core;

/** One API endpoint: its {@link Host} and the path relative to that host. All endpoints are declared once in {@code WebApiEndpoints}. */
public record Endpoint(Host host, String path) {

    public static Endpoint webApi(String path) {
        return new Endpoint(Host.WEB_API, path);
    }

    public static Endpoint finance(String path) {
        return new Endpoint(Host.FINANCE_API, path);
    }

    @Override
    public String toString() {
        return host + " " + path;
    }
}
