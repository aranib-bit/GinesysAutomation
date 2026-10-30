package com.gsl.validation.api;

import com.gsl.api.client.AuditLogApiClient;
import com.gsl.api.client.AuditLogApiClient.AuditDoc;
import com.gsl.api.model.audit.AuditRow;
import com.gsl.config.ConfigFactory;
import com.gsl.utils.Log;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

/**
 * Reads one voucher's audit trail through the Audit API: the module state, the FINVCHMAIN / FINVCHDET history (keyed by vchcode) and the
 * FINCOSTTAG site-allocation history (keyed by finpost code, found by the voucher number in the module's document list).
 * {@link #capture} is called before an action to remember where the trail stood, and again after it; {@link #awaitNewVersion} waits for the
 * audit rows the action wrote to become visible.
 */
public final class AuditApiReader {

    private AuditApiReader() {}

    /** The trail as it stood before the action, plus what is needed to read it again afterwards. */
    public record Before(Capture capture, String vchcode, LocalDate from) {}

    private static final ThreadLocal<Before> BEFORE = new ThreadLocal<>();

    /** The BEFORE capture taken by {@link #captureBefore} on this test thread, or null. */
    public static Before before() { return BEFORE.get(); }

    /** Reads (and remembers for this thread) the audit trail of the existing voucher {@code voucherNo} before the action under test. */
    public static Before captureBefore(String voucherNo) {
        BEFORE.remove();
        var row = NoDbApiSession.current().listRow(voucherNo);
        if (row == null) {
            Log.warn("[AuditApiReader] voucher {} is not listed - no BEFORE audit trail captured", voucherNo);
            return null;
        }
        LocalDate from = createdDate(row.getCreatedOn(), LocalDate.now());
        String vchcode = String.valueOf(row.getCode());
        Before b = new Before(capture(voucherNo, vchcode, from), vchcode, from);
        BEFORE.set(b);
        return b;
    }

    /** Site-allocation audit document codes seen per voucher number, so the trail stays readable after the voucher (and its list entry) is deleted. */
    private static final java.util.Map<String, java.util.Set<String>> SITE_DOC_CODES = new java.util.concurrent.ConcurrentHashMap<>();

    /** The audit trail of one voucher at one moment. {@code maxVersion} is the highest {@code versioN_SEQ} over FINVCHMAIN and FINVCHDET. */
    public record Capture(String voucherNo, String vchcode, boolean voucherModuleEnabled, boolean siteModuleEnabled,
                          List<AuditRow> main, List<AuditRow> det, List<AuditRow> site, int maxVersion, int siteMaxVersion) {

        public List<AuditRow> mainAfter(int version) { return after(main, version); }
        public List<AuditRow> detAfter(int version) { return after(det, version); }
        public List<AuditRow> siteAfter(int version) { return after(site, version); }

        /** The last FINVCHMAIN version at or below {@code version}, or null. */
        public AuditRow mainAt(int version) {
            return main.stream().filter(r -> r.version() <= version).max(Comparator.comparingInt(AuditRow::version)).orElse(null);
        }

        private static List<AuditRow> after(List<AuditRow> rows, int version) {
            return rows.stream().filter(r -> r.version() > version).toList();
        }
    }

    /**
     * The voucher's creation date from the rootview/list {@code createdOn} (ISO text), falling back to {@code fallback} with a warning when
     * it cannot be read — the document list of the Audit API is filtered by activity date, so it must start no later than this.
     */
    public static LocalDate createdDate(String createdOn, LocalDate fallback) {
        try {
            if (createdOn != null && createdOn.length() >= 10) return LocalDate.parse(createdOn.substring(0, 10));
        } catch (RuntimeException ignored) {
            // handled below
        }
        Log.warn("[AuditApiReader] voucher creation date '{}' is unreadable - searching the audit log from {}", createdOn, fallback);
        return fallback;
    }

    /** Reads the trail of {@code vchcode} (numbered {@code voucherNo}) as it stands now, searching the audit document list from {@code from} (the voucher's creation date) to today. */
    public static Capture capture(String voucherNo, String vchcode, LocalDate from) {
        LocalDate today = LocalDate.now();
        return com.gsl.validation.api.NoDbApiSession.current().audit(client -> {
            AuditLogApiClient.ModuleState voucher = client.fetchModuleState(AuditLogApiClient.MODULE_VOUCHER, today);
            AuditLogApiClient.ModuleState site = client.fetchModuleState(AuditLogApiClient.MODULE_SITE_ALLOCATION, today);
            List<AuditRow> main = client.fetchHistory(vchcode, "FINVCHMAIN", AuditLogApiClient.MODULE_VOUCHER, from, today);
            List<AuditRow> det = client.fetchHistory(vchcode, "FINVCHDET", AuditLogApiClient.MODULE_VOUCHER, from, today);
            List<AuditRow> siteRows = new ArrayList<>();
            String prefix = "Voucher : " + voucherNo + " :";
            // A deleted voucher's site-allocation document drops out of the audit document list, so the codes seen earlier are read directly.
            java.util.Set<String> codes = SITE_DOC_CODES.computeIfAbsent(voucherNo, k -> java.util.concurrent.ConcurrentHashMap.newKeySet());
            for (AuditDoc d : client.fetchDocuments(AuditLogApiClient.MODULE_SITE_ALLOCATION, from, today)) {
                if (d.docNo() != null && d.docNo().startsWith(prefix)) codes.add(String.valueOf(d.docCode()));
            }
            for (String code : codes) {
                siteRows.addAll(client.fetchHistory(code, "FINCOSTTAG", AuditLogApiClient.MODULE_SITE_ALLOCATION, from, today));
            }
            int max = 0;
            for (AuditRow r : main) max = Math.max(max, r.version());
            for (AuditRow r : det) max = Math.max(max, r.version());
            int siteMax = siteRows.stream().mapToInt(AuditRow::version).max().orElse(0);
            return new Capture(voucherNo, vchcode, voucher != null && voucher.enabled(), site != null && site.enabled(),
                    main, det, siteRows, max, siteMax);
        });
    }

    /**
     * Polls {@link #capture} until {@code ready} holds or {@code audit.api.wait.seconds} elapses (the audit rows are written by the
     * application, so they may trail the action by a moment); returns the last capture either way so the checks report what is there.
     */
    public static Capture awaitNewVersion(String voucherNo, String vchcode, LocalDate from, Predicate<Capture> ready) {
        long deadline = System.currentTimeMillis() + ConfigFactory.getConfig().auditApiWaitSeconds() * 1000L;
        Capture last = capture(voucherNo, vchcode, from);
        while (!ready.test(last) && System.currentTimeMillis() < deadline) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
            last = capture(voucherNo, vchcode, from);
        }
        if (!ready.test(last)) Log.warn("[AuditApiReader] expected audit rows for {} did not appear within the wait", voucherNo);
        return last;
    }
}
