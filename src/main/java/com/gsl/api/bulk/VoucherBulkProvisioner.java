package com.gsl.api.bulk;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gsl.api.core.DataSetupException;
import com.gsl.api.model.common.VoucherActionRequest;
import com.gsl.api.model.common.VoucherDeleteRequest;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.LongFunction;

/**
 * Voucher lifecycle in bulk through k6: revert (Unpost), release (Post) and delete of AP, AR and General vouchers the framework already created.
 * One body per voucher, so each k6 iteration is one voucher and the load spreads over the VUs. Java then reads every voucher's status back
 * (through the module's own client, passed in as a function) and counts those now in the expected state, so the result does not rest on k6's
 * counters alone. A refusal is HTTP 200 with a message per voucher, which the k6 flow treats as a failure.
 */
public final class VoucherBulkProvisioner {

    public enum Module {
        AP("AP"), AR("AR"), GV("GV");

        private final String prefix;

        Module(String prefix) {
            this.prefix = prefix;
        }

        String endpointName(Action action) {
            return prefix + "_" + action.name();
        }
    }

    /** What to do to the vouchers and which state proves it worked ({@code null} = the voucher no longer exists). */
    public enum Action {
        CREATE("POST", null), REVERT("PUT", "U"), RELEASE("PUT", "P"), DELETE("DELETE", null);

        private final String method;
        private final String expectedStatus;

        Action(String method, String expectedStatus) {
            this.method = method;
            this.expectedStatus = expectedStatus;
        }

        public String expectedStatus() {
            return expectedStatus;
        }
    }

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private VoucherBulkProvisioner() {}

    /** What a create run produced: k6's report, how many were requested, and the voucher codes k6 logged for the creates that succeeded. */
    public record CreateResult(K6Result k6, int requested, List<Long> vchcodes) {
        /** k6 ended cleanly and every requested voucher reported a code. */
        public boolean complete() {
            return k6.passed() && vchcodes.size() == requested;
        }
    }

    /** {@code <tag> [VU n iter m] OK id=... status=... code=<vchcode> ms=...} as {@code bulkPost} logs a successful voucher create. */
    private static final java.util.regex.Pattern CREATED_LINE = java.util.regex.Pattern.compile("\\] OK .* code=(\\d+)\\b");

    /** The voucher code on a k6 OK line of a voucher create, or null for any other line. */
    public static Long parseCreatedCode(String line) {
        java.util.regex.Matcher m = CREATED_LINE.matcher(line);
        return m.find() ? Long.parseLong(m.group(1)) : null;
    }

    /**
     * Creates the vouchers through k6: {@code requests} are the bodies built by the module's service ({@code buildBulkRequests}); each k6
     * iteration posts one. The voucher codes come back on k6's own OK lines, so a following revert/release/delete run can use them.
     */
    public static CreateResult create(Module module, List<?> requests, int vus, String runId, String tenant, Consumer<String> lineHandler) {
        if (requests.isEmpty()) throw new DataSetupException("[VoucherBulkProvisioner] no voucher requests to create");
        Map<String, Object> file = new LinkedHashMap<>();
        file.put("logTag", "Create" + module.name() + "Voucher");
        file.put("endpointGroup", "Lifecycle");
        file.put("endpoint", module.endpointName(Action.CREATE));
        file.put("runId", runId);
        file.put("expect", "voucher-created");
        List<Object> bodies = new ArrayList<>(requests.size());
        for (Object r : requests) bodies.add(MAPPER.convertValue(r, Map.class));
        file.put("bodies", bodies);
        Path path = BulkDataFiles.write("vouchers-" + module.name().toLowerCase() + "-create", runId, file);

        List<Long> codes = new ArrayList<>();
        Consumer<String> collect = line -> {
            Long code = parseCreatedCode(line);
            if (code != null) codes.add(code);
            lineHandler.accept(line);
        };
        K6Result k6 = K6Runner.configured().run(K6Scripts.resolve("bulkPost"), PartyBulkProvisioner.env(path, requests.size(), vus, tenant), collect);
        CreateResult result = new CreateResult(k6, requests.size(), List.copyOf(codes));
        if (result.complete()) BulkDataFiles.deleteQuietly(path);
        return result;
    }

    /**
     * @param statusOf the voucher's document status ("P" posted, "U" unposted) or null when it no longer exists, e.g.
     *                 {@code code -> client.fetchDocumentStatus(code, ou, user)}
     * @throws DataSetupException when there is nothing to do or k6 cannot be started
     */
    public static PartyBulkProvisioner.Result run(Module module, Action action, List<Long> vchcodes, long empCode, long ouCode, int vus,
                                                  String runId, LongFunction<String> statusOf, String tenant, Consumer<String> lineHandler) {
        if (vchcodes.isEmpty()) throw new DataSetupException("[VoucherBulkProvisioner] no vouchers to " + action.name().toLowerCase());
        Path file = writeFile(module, action, vchcodes, empCode, ouCode, runId);
        K6Result k6 = K6Runner.configured().run(K6Scripts.resolve("bulkPost"), PartyBulkProvisioner.env(file, vchcodes.size(), vus, tenant), lineHandler);
        PartyBulkProvisioner.Result result = new PartyBulkProvisioner.Result(k6, vchcodes.size(), countInState(vchcodes, statusOf, action));
        if (result.complete()) BulkDataFiles.deleteQuietly(file);
        return result;
    }

    /** The vouchers whose status now equals the action's expected state. */
    public static int countInState(List<Long> vchcodes, LongFunction<String> statusOf, Action action) {
        int n = 0;
        for (long code : vchcodes) {
            String status = statusOf.apply(code);
            if (action.expectedStatus() == null ? status == null : action.expectedStatus().equalsIgnoreCase(status)) n++;
        }
        return n;
    }

    /** One body per voucher in the shape the application sends: {@code {code:[n], empCode, connectedOUCode}} or, for delete, {@code {voucherCodes:[n], ...}}. */
    public static List<Object> bodies(Action action, List<Long> vchcodes, long empCode, long ouCode) {
        List<Object> bodies = new ArrayList<>(vchcodes.size());
        for (long code : vchcodes) {
            Object request = action == Action.DELETE
                    ? VoucherDeleteRequest.builder().empCode(empCode).connectedOUCode(ouCode).voucherCodes(List.of(code)).build()
                    : VoucherActionRequest.builder().code(List.of(code)).empCode(empCode).connectedOUCode(ouCode).build();
            bodies.add(MAPPER.convertValue(request, Map.class));
        }
        return bodies;
    }

    private static Path writeFile(Module module, Action action, List<Long> vchcodes, long empCode, long ouCode, String runId) {
        Map<String, Object> file = new LinkedHashMap<>();
        file.put("logTag", capitalise(module.name()) + capitalise(action.name()));
        file.put("endpointGroup", "Lifecycle");
        file.put("endpoint", module.endpointName(action));
        file.put("method", action.method);
        file.put("runId", runId);
        // A refusal (e.g. "Adjustment(s) made against this record") is still HTTP 200: every item3 must say success.
        file.put("expect", "action-items");
        file.put("bodies", bodies(action, vchcodes, empCode, ouCode));
        return BulkDataFiles.write("vouchers-" + module.name().toLowerCase() + "-" + action.name().toLowerCase(), runId, file);
    }

    private static String capitalise(String s) {
        return s.charAt(0) + s.substring(1).toLowerCase();
    }
}
