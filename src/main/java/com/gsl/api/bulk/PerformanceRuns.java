package com.gsl.api.bulk;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Launches the read-only k6 performance scenarios ({@code scenarios/performance}) and builds their environment. Latency budgets are never
 * invented here: a threshold is enforced only when the caller passes one, and a crossed threshold comes back as
 * {@link K6Status#THRESHOLDS_FAILED} in the {@link K6Result} (a result, not an exception).
 */
public final class PerformanceRuns {

    private PerformanceRuns() {}

    /** Runs the performance scenario of {@code scriptKey} ({@code auditReads}, {@code listParties}, ...) with {@code env}. */
    public static K6Result run(String scriptKey, Map<String, String> env, Consumer<String> lineHandler) {
        return K6Runner.configured().run(K6Scripts.resolve(scriptKey), env, lineHandler);
    }

    /**
     * Environment of {@code scenarios/performance/auditReads.js}.
     *
     * @param tables      history tables read per document; empty = only the module and document-list calls
     * @param listP95Ms   p95 budget of the document list in ms, or null for none
     * @param historyP95Ms p95 budget of one history call in ms, or null for none
     */
    public static Map<String, String> auditReadsEnv(String tenant, String module, int days, List<String> tables, int docsPerIteration, int vus,
                                                    String duration, Integer listP95Ms, Integer historyP95Ms) {
        Map<String, String> env = base(tenant, vus, duration);
        env.put("AUDIT_MODULE", module);
        env.put("AUDIT_DAYS", String.valueOf(days));
        env.put("AUDIT_TABLES", String.join(",", tables));
        env.put("AUDIT_DOCS", String.valueOf(docsPerIteration));
        if (listP95Ms != null) env.put("AUDIT_LIST_P95_MS", String.valueOf(listP95Ms));
        if (historyP95Ms != null) env.put("AUDIT_HISTORY_P95_MS", String.valueOf(historyP95Ms));
        return env;
    }

    /**
     * Environment of {@code scenarios/performance/listParties.js}.
     *
     * @param family VENDOR or CUSTOMER
     * @param slIdPrefix only parties whose sub-ledger id starts with this, or null for the whole list
     * @param pageP95Ms p95 budget of one list page in ms, or null for none
     */
    public static Map<String, String> listPartiesEnv(String tenant, String family, int site, int ou, String slIdPrefix, int vus, String duration, Integer pageP95Ms) {
        Map<String, String> env = base(tenant, vus, duration);
        env.put("PARTY_FAMILY", family);
        env.put("CONNECTED_SITE", String.valueOf(site));
        env.put("CONNECTED_OU", String.valueOf(ou));
        if (slIdPrefix != null && !slIdPrefix.isBlank()) env.put("SLID_PREFIX", slIdPrefix);
        if (pageP95Ms != null) env.put("LIST_PAGE_P95_MS", String.valueOf(pageP95Ms));
        return env;
    }

    private static Map<String, String> base(String tenant, int vus, String duration) {
        Map<String, String> env = new LinkedHashMap<>();
        env.put("TENANT", tenant);
        if (vus > 0) env.put("TARGET_VUS", String.valueOf(vus));
        if (duration != null && !duration.isBlank()) env.put("DURATION", duration);
        return env;
    }
}