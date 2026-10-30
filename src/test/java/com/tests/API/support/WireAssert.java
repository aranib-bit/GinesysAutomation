package com.tests.API.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Set;

import static org.testng.Assert.assertEquals;

/** Wire-compatibility checks: a POJO must serialize to the JSON captured in a HAR (under {@code har-samples/}), apart from named fields. */
public final class WireAssert {

    private static final ObjectMapper JSON = new ObjectMapper();

    private static final java.util.Comparator<JsonNode> NUMERIC_TOLERANT = (a, b) -> {
        if (a.isNumber() && b.isNumber()) return a.decimalValue().compareTo(b.decimalValue());
        return a.equals(b) ? 0 : 1;
    };

    private WireAssert() {}

    /** Reads {@code src/test/resources/har-samples/<resource>} as text. */
    public static String sample(String resource) {
        try (InputStream in = WireAssert.class.getClassLoader().getResourceAsStream("har-samples/" + resource)) {
            if (in == null) throw new IllegalStateException("Missing HAR sample har-samples/" + resource);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Asserts {@code pojo} serializes to the same JSON tree as the sample, ignoring the top-level fields in {@code ignored}. */
    public static void sameJson(Object pojo, String resource, Set<String> ignored) {
        try {
            JsonNode actual = JSON.valueToTree(pojo);
            JsonNode expected = JSON.readTree(sample(resource));
            strip(actual, ignored);
            strip(expected, ignored);
            // Numbers compare by value: a POJO long serializes as a LongNode while the parsed sample holds an IntNode.
            if (!expected.equals(NUMERIC_TOLERANT, actual)) {
                assertEquals(actual, expected, "POJO JSON differs from har-samples/" + resource);
            }
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    public static void sameJson(Object pojo, String resource) {
        sameJson(pojo, resource, Set.of());
    }

    private static void strip(JsonNode node, Set<String> ignored) {
        if (node instanceof ObjectNode obj) obj.remove(ignored);
    }

    public static <T> T read(String resource, Class<T> type) {
        try {
            return JSON.readValue(sample(resource), type);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
