package com.gsl.api.bulk;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gsl.api.client.DocumentAdjustmentApiClient;
import com.gsl.api.core.DataSetupException;
import com.gsl.api.model.common.SubLedgerLookupResult;
import com.gsl.api.model.documentadjustment.DocumentAdjustmentUntagRequest;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Document adjustment through k6: the read-only Select Document / Reference Site journey (data file of sub-ledger targets) and the untag of
 * documents the caller names. Untag changes data, so it only ever touches the explicit {@link UntagTarget}s passed in (the framework's own
 * throw-away vouchers); it never searches for documents to untag.
 */
public final class DocumentAdjustmentBulk {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** One document to clear all adjustments from: its own code, number, actual amount and sub-ledger, never the counter-document's. */
    public record UntagTarget(long vchcode, String docNo, BigDecimal actualAmount, long slCode) {}

    private DocumentAdjustmentBulk() {}

    /**
     * Writes the sub-ledger targets of the read scenario ({@code scenarios/performance/documentAdjustmentReads.js}).
     *
     * @param module {@link DocumentAdjustmentApiClient#MODULE_AP} or {@link DocumentAdjustmentApiClient#MODULE_AR}
     * @param date   {@code yyyy-MM-ddT00:00:00}
     * @throws DataSetupException when there is no sub-ledger to read
     */
    public static Path writeReadTargets(String module, int ouCode, String date, List<SubLedgerLookupResult> subLedgers, String runId) {
        if (subLedgers.isEmpty()) throw new DataSetupException("[DocumentAdjustmentBulk] no sub-ledgers to read pending documents for");
        List<Object> targets = new ArrayList<>(subLedgers.size());
        for (SubLedgerLookupResult sl : subLedgers) {
            Map<String, Object> t = new LinkedHashMap<>();
            t.put("glCode", sl.getGlCode());
            t.put("slCode", sl.getSlCode());
            targets.add(t);
        }
        Map<String, Object> file = new LinkedHashMap<>();
        file.put("module", module);
        file.put("endpointPrefix", prefixOf(module));
        file.put("docType", DocumentAdjustmentApiClient.BASE_DOCUMENT_TYPE_VOUCHER);
        file.put("ouCode", ouCode);
        file.put("date", date);
        file.put("runId", runId);
        file.put("targets", targets);
        return BulkDataFiles.write("docadj", runId, file);
    }

    /** Environment of the read scenario; budgets are set only when given (none are invented). */
    public static Map<String, String> readsEnv(Path file, String tenant, int vus, String duration, Integer pendingP95Ms, Integer referenceP95Ms) {
        Map<String, String> env = new LinkedHashMap<>();
        env.put("DOC_ADJ_FILE", file.toAbsolutePath().toString());
        env.put("TENANT", tenant);
        if (vus > 0) env.put("TARGET_VUS", String.valueOf(vus));
        if (duration != null && !duration.isBlank()) env.put("DURATION", duration);
        if (pendingP95Ms != null) env.put("DOCADJ_PENDING_P95_MS", String.valueOf(pendingP95Ms));
        if (referenceP95Ms != null) env.put("DOCADJ_REFERENCE_P95_MS", String.valueOf(referenceP95Ms));
        return env;
    }

    /** Runs the read scenario against {@code file}; a crossed threshold comes back as a result, not an exception. */
    public static K6Result runReads(Path file, String tenant, int vus, String duration, Integer pendingP95Ms, Integer referenceP95Ms, Consumer<String> lineHandler) {
        return PerformanceRuns.run("documentAdjustmentReads", readsEnv(file, tenant, vus, duration, pendingP95Ms, referenceP95Ms), lineHandler);
    }

    /** One untag body per document ({@code AdjustedDocuments} empty clears every tag), in the application's own shape. */
    public static List<Object> untagBodies(List<UntagTarget> targets, long empCode, long ouCode) {
        List<Object> bodies = new ArrayList<>(targets.size());
        for (UntagTarget t : targets) {
            bodies.add(MAPPER.convertValue(DocumentAdjustmentUntagRequest.builder().connectedUserCode(empCode).connectedOUCode(ouCode)
                    .baseDocumentType(DocumentAdjustmentApiClient.BASE_DOCUMENT_TYPE_VOUCHER).baseDocumentCode(t.vchcode()).baseDocumentNumber(t.docNo())
                    .baseDocumentActualAmount(t.actualAmount()).baseDocumentSlCode(t.slCode()).build(), Map.class));
        }
        return bodies;
    }

    /**
     * Untags exactly the given documents through k6, one document per iteration. The endpoint answers {@code success:true} with the document
     * number; there is no cheap tag-state read-back, so the result is k6's own count of accepted untags.
     *
     * @throws DataSetupException when {@code targets} is empty (this never searches for documents to untag)
     */
    public static K6Result untag(List<UntagTarget> targets, long empCode, long ouCode, int vus, String runId, String tenant, Consumer<String> lineHandler) {
        if (targets.isEmpty()) throw new DataSetupException("[DocumentAdjustmentBulk] no documents named to untag");
        Map<String, Object> file = new LinkedHashMap<>();
        file.put("logTag", "UntagDocument");
        file.put("endpointGroup", "DocumentAdjustment");
        file.put("endpoint", "UNTAG");
        file.put("runId", runId);
        file.put("expect", "success-only");
        file.put("bodies", untagBodies(targets, empCode, ouCode));
        Path path = BulkDataFiles.write("docadj-untag", runId, file);
        K6Result result = K6Runner.configured().run(K6Scripts.resolve("bulkPost"), PartyBulkProvisioner.env(path, targets.size(), vus, tenant), lineHandler);
        if (result.passed() && result.getCreated() == targets.size()) BulkDataFiles.deleteQuietly(path);
        return result;
    }

    /** {@code apvoucher} -> {@code AP}, {@code arvoucher} -> {@code AR}: the prefix of the generated endpoint names. */
    static String prefixOf(String module) {
        if (DocumentAdjustmentApiClient.MODULE_AP.equals(module)) return "AP";
        if (DocumentAdjustmentApiClient.MODULE_AR.equals(module)) return "AR";
        throw new IllegalArgumentException("Unknown document adjustment module '" + module + "'");
    }
}
