package com.tests.API;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.gsl.api.core.Endpoint;
import com.gsl.api.webapi.WebApiEndpoints;
import com.gsl.api.webapi.factory.PartyRequestFactory;
import org.testng.annotations.Test;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

/**
 * The Java side owns the API contract; k6 reads it. This test renders the contract files the k6 flows import (endpoint registry, default party
 * create bodies) and compares them with the committed copies under {@code performance/k6}, so the two runtimes cannot drift silently.
 * Regenerate with {@code -Dk6.contract.write=true}.
 */
public class K6ContractTest {

    private static final Path K6_HOME = Path.of("performance", "k6");
    private static final String HEADER = "// GENERATED from com.gsl.api.webapi.WebApiEndpoints by K6ContractTest - do not edit; regenerate with -Dk6.contract.write=true\n";
    private static final ObjectMapper MAPPER = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    /** Every contract file: relative path under the k6 home -> content. */
    static Map<String, String> render() throws Exception {
        Map<String, String> files = new LinkedHashMap<>();
        files.put("lib/generated/endpoints.generated.js", endpointsJs());
        PartyRequestFactory.Identity blank = PartyRequestFactory.Identity.withPlaceholderContact("", "", "", 0, 0);
        files.put("data/templates/vendorMasterCreate.json",
                MAPPER.writeValueAsString(PartyRequestFactory.vendor(blank, 0, 0, 0, 0, null, null, null, null)) + "\n");
        files.put("data/templates/customerCreate.json",
                MAPPER.writeValueAsString(PartyRequestFactory.customer(blank, 0, 0, 0, null, null, 0, 0, null)) + "\n");
        return files;
    }

    private static String endpointsJs() throws IllegalAccessException {
        StringBuilder js = new StringBuilder(HEADER);
        js.append("// host WEB_API -> apiPrefix (/WebAPI/api), FINANCE_API -> financeApiPrefix (/Finance/api); see lib/api.js\n");
        for (Class<?> group : WebApiEndpoints.class.getDeclaredClasses()) {
            js.append("export const ").append(group.getSimpleName()).append(" = {\n");
            for (Field f : group.getDeclaredFields()) {
                if (!Modifier.isStatic(f.getModifiers()) || f.getType() != Endpoint.class) continue;
                Endpoint e = (Endpoint) f.get(null);
                js.append("  ").append(f.getName()).append(": { host: '").append(e.host().name()).append("', path: '").append(e.path()).append("' },\n");
            }
            js.append("};\n");
        }
        return js.toString();
    }

    @Test
    public void committedK6ContractMatchesTheJavaContract() throws Exception {
        boolean write = Boolean.getBoolean("k6.contract.write");
        for (Map.Entry<String, String> file : render().entrySet()) {
            Path target = K6_HOME.resolve(file.getKey());
            if (write) {
                Files.createDirectories(target.getParent());
                Files.writeString(target, file.getValue());
            }
            assertTrue(Files.isRegularFile(target), target + " is missing; run with -Dk6.contract.write=true");
            assertEquals(read(target), file.getValue().replace("\r\n", "\n"),
                    target + " differs from the Java contract; run with -Dk6.contract.write=true");
        }
    }

    @Test
    public void theEndpointRegistryListsThePartyEndpoints() throws Exception {
        String js = endpointsJs();
        assertTrue(js.contains("VENDOR_CREATE: { host: 'WEB_API', path: '/VendorMaster/Create' }"), js);
        assertTrue(js.contains("CUSTOMER_CREATE"), js);
    }

    private static String read(Path p) throws IOException {
        return Files.readString(p).replace("\r\n", "\n");
    }
}
