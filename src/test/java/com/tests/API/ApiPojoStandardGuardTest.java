package com.tests.API;

import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.testng.Assert.assertTrue;

/**
 * Keeps the API POJO standard (docs/conventions/api-pojo-standard.md) from eroding: the clients in {@code com.gsl.api.client} must not carry endpoint
 * string literals, hand-built {@code Map} request bodies or untyped {@code jsonPath()} reads. What is still there is recorded in {@link #BASELINE}, with
 * the reason; the count of a file may only go down, and a new client must start clean. When a count drops, lower the baseline in the same change.
 */
public class ApiPojoStandardGuardTest {

    private static final Path CLIENTS = Path.of("src/main/java/com/gsl/api/client");

    private static final Map<String, Pattern> RULES = Map.of(
            "LITERAL", Pattern.compile("\\b(get|post|put|delete|patch)(Raw|RawExpectFailure|ExpectFailure)?\\(\\s*\"/"),
            "BODY_MAP", Pattern.compile("Map<String, Object> body\\b|\\bbody\\.put\\("),
            "UNTYPED_JSONPATH", Pattern.compile("jsonPath\\(\\)\\.(getString|getInt|getLong|getBoolean|getMap|get)\\(|getList\\(\"[^\"]*\"\\)"));

    /**
     * file -> rule -> allowed count. Reasons: Vendor/Customer/Item read lookup rows whose columns differ per master, and Vendor patches the full vendor
     * record it read (GET, change, POST Update); DocSchemeAdmin patches the full site entity the same way; Audit rows have per-table columns; the two
     * paging totals are single scalars of a paged result.
     */
    private static final Map<String, Map<String, Integer>> BASELINE = Map.of(
            "AuditLogApiClient.java", Map.of("UNTYPED_JSONPATH", 3),
            "CustomerApiClient.java", Map.of("BODY_MAP", 1, "UNTYPED_JSONPATH", 1),
            "DocSchemeAdminApiClient.java", Map.of("BODY_MAP", 5),
            "DocumentAdjustmentApiClient.java", Map.of("UNTYPED_JSONPATH", 1),
            "GeneralVoucherApiClient.java", Map.of("UNTYPED_JSONPATH", 1),
            "ItemApiClient.java", Map.of("UNTYPED_JSONPATH", 3),
            "VendorApiClient.java", Map.of("UNTYPED_JSONPATH", 8, "BODY_MAP", 7));

    @Test
    public void clientsStayWithinTheRecordedBaseline() throws IOException {
        List<String> breaches = new ArrayList<>();
        try (Stream<Path> files = Files.list(CLIENTS)) {
            for (Path file : files.filter(f -> f.getFileName().toString().endsWith("Client.java")).sorted().toList()) {
                String name = file.getFileName().toString();
                Map<String, Integer> found = new TreeMap<>();
                for (String line : Files.readAllLines(file)) {
                    String trimmed = line.strip();
                    if (trimmed.startsWith("*") || trimmed.startsWith("//") || trimmed.startsWith("/*")) continue;
                    RULES.forEach((rule, pattern) -> {
                        if (pattern.matcher(line).find()) found.merge(rule, 1, Integer::sum);
                    });
                }
                Map<String, Integer> allowed = BASELINE.getOrDefault(name, Map.of());
                found.forEach((rule, count) -> {
                    int limit = allowed.getOrDefault(rule, 0);
                    if (count > limit) breaches.add(name + ": " + rule + " " + count + " > allowed " + limit);
                });
            }
        }
        assertTrue(breaches.isEmpty(), "API clients break the POJO standard (see docs/conventions/api-pojo-standard.md):\n  " + String.join("\n  ", breaches));
    }
}
