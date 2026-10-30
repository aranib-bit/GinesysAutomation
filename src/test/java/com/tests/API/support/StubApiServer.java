package com.tests.API.support;

import com.sun.net.httpserver.HttpServer;
import io.restassured.RestAssured;
import io.restassured.specification.RequestSpecification;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * A JDK {@link HttpServer} on a random port that plays the application for client unit tests: it records every request and answers with a
 * canned status/body per {@code "METHOD path"}. Shared by the per-domain client tests so none re-implements it.
 */
public final class StubApiServer implements AutoCloseable {

    /** One request the server saw. */
    public record Seen(String method, String path, String query, String body) {}

    private record Canned(int status, String body) {}

    private final HttpServer server;
    private final List<Seen> seen = new CopyOnWriteArrayList<>();
    private final Map<String, Canned> canned = new ConcurrentHashMap<>();

    private StubApiServer(HttpServer server) {
        this.server = server;
    }

    public static StubApiServer start() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            StubApiServer stub = new StubApiServer(server);
            server.createContext("/", exchange -> {
                String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                String path = exchange.getRequestURI().getPath();
                stub.seen.add(new Seen(exchange.getRequestMethod(), path, exchange.getRequestURI().getRawQuery(), body));
                Canned c = stub.canned.getOrDefault(exchange.getRequestMethod() + " " + path, new Canned(200, "{\"success\":true,\"result\":null}"));
                byte[] bytes = c.body().getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(c.status(), bytes.length);
                exchange.getResponseBody().write(bytes);
                exchange.close();
            });
            server.start();
            return stub;
        } catch (IOException e) {
            throw new IllegalStateException("Cannot start the stub API server", e);
        }
    }

    public String base() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    /** A session-like spec pointing at this server. */
    public RequestSpecification spec() {
        return RestAssured.given().baseUri(base()).contentType("application/json");
    }

    /** Answers {@code method path} with {@code status} and {@code body}. */
    public void respond(String method, String path, int status, String body) {
        canned.put(method + " " + path, new Canned(status, body));
    }

    public List<Seen> seen() {
        return seen;
    }

    public Seen last() {
        if (seen.isEmpty()) throw new AssertionError("no request reached the stub server");
        return seen.get(seen.size() - 1);
    }

    /** Forgets recorded requests and canned answers. */
    public void reset() {
        seen.clear();
        canned.clear();
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
