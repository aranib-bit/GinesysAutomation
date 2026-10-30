package com.gsl.utils.dataprovider.source.finance;

import com.gsl.api.client.DocumentAdjustmentApiClient;
import com.gsl.api.core.ApiSessionFactory;
import com.gsl.api.model.documentadjustment.PendingDocument;
import com.gsl.model.finance.PendingSlDocument;
import com.gsl.reports.ReportLogger;
import com.gsl.utils.Log;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * "Refresh live" right before the UI uses the reserved documents: the API selector records what it reserved (document number and pending amount per
 * sub-ledger) for the current thread, and {@link #verifyCurrentThread()} re-reads those sub-ledgers just before the Adjust step and fails fast, with a
 * precise message, when a reserved document is gone or its pending amount changed since selection. Nothing is retried or waited for: a change means a
 * document was consumed outside this scenario, which is the diagnostic to surface instead of the server's later, cryptic save error.
 *
 * <p>Thread-local on purpose: the scenario's data is resolved and its UI flow runs on the same test thread (the document reservations are tracked the
 * same way). With nothing recorded (DB-mode data, advance scenarios) {@link #verifyCurrentThread()} does nothing.
 */
public final class PendingDocumentRecheck {

    /** One sub-ledger's reserved documents as selected, plus what is needed to read them again. */
    private record Claim(ApiPendingDocumentSelector.Domain domain, long glCode, long slCode, int ouCode, String date, String slKey,
                         Map<String, BigDecimal> pendingByDocument) {}

    private static final ThreadLocal<Map<String, Claim>> CLAIMS = ThreadLocal.withInitial(LinkedHashMap::new);

    private PendingDocumentRecheck() {}

    /** Forgets this thread's claims; called when a scenario's data resolution starts, so an earlier scenario's claims never leak into the next. */
    public static void clearCurrentThread() {
        CLAIMS.remove();
    }

    /** Records what the selector reserved for one sub-ledger (all of it for an exclusive claim, otherwise the documents the scenario uses). */
    static void record(ApiPendingDocumentSelector.Domain domain, long glCode, long slCode, int ouCode, String date, String slKey, List<PendingSlDocument> docs) {
        Map<String, BigDecimal> pending = new LinkedHashMap<>();
        docs.forEach(d -> pending.put(d.getDocumentNo(), d.getPending()));
        CLAIMS.get().put(slKey, new Claim(domain, glCode, slCode, ouCode, date, slKey, pending));
    }

    /**
     * Re-reads every recorded sub-ledger and fails when a reserved document disappeared or its pending amount changed.
     *
     * @throws AssertionError listing each changed document, with the selected and the current pending amount
     */
    public static void verifyCurrentThread() {
        if (CLAIMS.get().isEmpty()) return;
        ApiSessionFactory.withWebApiSession(session -> {
            verify(session.getDocumentAdjustmentApiClient());
            return null;
        });
    }

    /** {@link #verifyCurrentThread()} against an already-open client (the claims are consumed either way). */
    public static void verify(DocumentAdjustmentApiClient client) {
        Map<String, Claim> claims = CLAIMS.get();
        if (claims.isEmpty()) return;
        List<Claim> toCheck = new ArrayList<>(claims.values());
        claims.clear();
        List<String> problems = new ArrayList<>();
        for (Claim claim : toCheck) {
            Map<String, BigDecimal> current = currentPending(client, claim);
            claim.pendingByDocument().forEach((docNo, selected) -> {
                BigDecimal now = current.get(docNo);
                if (now == null) {
                    problems.add(claim.slKey() + ": document " + docNo + " is no longer pending (selected pending " + selected + ")");
                } else if (now.compareTo(selected) != 0) {
                    problems.add(claim.slKey() + ": document " + docNo + " pending " + selected + " -> " + now + " since selection");
                }
            });
        }
        if (problems.isEmpty()) {
            Log.info("[PendingRecheck] {} sub-ledger claim(s) re-read live: all reserved documents unchanged.", toCheck.size());
            return;
        }
        String message = "[API recheck] reserved pending documents changed before the UI step, consumed outside this scenario:\n  " + String.join("\n  ", problems);
        ReportLogger.warn(message);
        throw new AssertionError(message);
    }

    private static Map<String, BigDecimal> currentPending(DocumentAdjustmentApiClient client, Claim claim) {
        Map<String, BigDecimal> now = new LinkedHashMap<>();
        for (PendingDocument d : client.fetchPendingDocuments(claim.domain().module(), claim.glCode(), claim.slCode(), claim.ouCode(), claim.date())) {
            if (d.getDocument_No() == null || d.getPending() == null || d.getPending().signum() == 0) continue;
            now.put(d.getDocument_No(), ApiPendingDocumentSelector.effectivePending(d));
        }
        return now;
    }
}
