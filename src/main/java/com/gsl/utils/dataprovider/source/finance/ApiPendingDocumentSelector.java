package com.gsl.utils.dataprovider.source.finance;

import com.gsl.api.client.DocumentAdjustmentApiClient;
import com.gsl.api.model.common.SubLedgerLookupResult;
import com.gsl.api.model.documentadjustment.PendingDocument;
import com.gsl.config.ConfigFactory;
import com.gsl.database.DocumentReservationRegistry;
import com.gsl.database.DocumentSelectionResult;
import com.gsl.database.query.PendingAdjQuery;
import com.gsl.enums.DocumentSelectionMode;
import com.gsl.model.finance.PendingSlDocument;
import com.gsl.model.finance.SlNameWithId;
import com.gsl.utils.Log;
import org.testng.SkipException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * API twin of the DB pending-document selection ({@code DbApVoucherDataSource.selectApDocumentsAtomic} and
 * {@code DocumentSelector.selectAtomic}), shared by AP and AR: walks the candidate sub-ledgers, asks
 * {@code DocumentAdjustments/{apvoucher|arvoucher}} for each one's pending documents, and keeps the first
 * {@code subLedgerCount} that still have {@code documentCount} documents this thread can reserve.
 *
 * <p>Reservation is unchanged: the very same in-memory {@link DocumentReservationRegistry} (key = document number) the DB path uses,
 * so parallel API tests never get the same document and DB and API tests would not collide either. Nothing is written anywhere.
 *
 * <p>Cost control: each probe is one API call, so candidates are ordered by classification (suppliers first for AP, customers first for
 * AR) and probing stops at the first {@code subLedgerCount} hits, bounded by {@code api.pending.sl.probe.max}.
 */
public final class ApiPendingDocumentSelector {

    /** System property capping how many sub-ledgers one selection probes before declaring a data shortfall. */
    private static final String PROBE_MAX_PROPERTY = "api.pending.sl.probe.max";
    private static final int PROBE_MAX_DEFAULT = 60;

    private ApiPendingDocumentSelector() {}

    /** What distinguishes AP from AR. */
    public record Domain(String module, String debitCredit, String preferredClassType, String label) {
        public static final Domain AP = new Domain(DocumentAdjustmentApiClient.MODULE_AP, "C", "S", "AP");
        public static final Domain AR = new Domain(DocumentAdjustmentApiClient.MODULE_AR, "D", "C", "AR");
    }

    /**
     * @param candidates       sub-ledgers offered by the module's lookup (already class-filtered by the caller when a class is required)
     * @param requireSingleRef when true, SLs whose pending documents span more than one reference site are skipped (plain FIFO needs one)
     * @param docTypeFilter    {@code entype} to keep, or null for any
     * @param exclusiveSl      true for scenarios where the UI allocates across the sub-ledger's whole pending list (FIFO / reference-site-wise
     *                         FIFO): the scenario then holds every pending document of the chosen sub-ledger, so no other scenario can pick one
     * @throws SkipException when fewer than {@code subLedgerCount} usable SLs exist (a data shortfall, handled by the backfill guard)
     */
    public static DocumentSelectionResult select(DocumentAdjustmentApiClient client, Domain domain, List<SubLedgerLookupResult> candidates,
                                                 int ouCode, String date, int subLedgerCount, int documentCount,
                                                 boolean requireSingleRef, String docTypeFilter, boolean exclusiveSl) {
        int needSls = Math.max(1, subLedgerCount);
        int needDocs = Math.max(1, documentCount);
        int probeMax = Integer.getInteger(PROBE_MAX_PROPERTY, PROBE_MAX_DEFAULT);
        DocumentSelectionMode mode = DocumentSelectionMode.fromString(ConfigFactory.getWaitConfig().docSelectorMode());

        PendingDocumentRecheck.clearCurrentThread();
        List<SubLedgerLookupResult> ordered = candidates.stream()
                .sorted(Comparator.comparingInt((SubLedgerLookupResult s) -> domain.preferredClassType().equalsIgnoreCase(s.getClassType()) ? 0 : 1))
                .collect(Collectors.toList());

        List<SlNameWithId> kept = new ArrayList<>();
        Map<String, List<PendingSlDocument>> keptDocs = new LinkedHashMap<>();
        int probed = 0;
        for (SubLedgerLookupResult sl : ordered) {
            if (kept.size() >= needSls || probed >= probeMax) break;
            probed++;
            List<PendingDocument> raw = client.fetchPendingDocuments(domain.module(), sl.getGlCode(), sl.getSlCode(), ouCode, date);
            List<PendingSlDocument> docs = toDocs(raw, sl, domain, docTypeFilter, mode);
            if (docs.size() < needDocs) continue;
            if (requireSingleRef && docs.stream().map(PendingSlDocument::getReferenceSite).distinct().count() > 1) continue;

            String key = PendingAdjQuery.compositeKey(sl.getSlName(), sl.getSlid());
            if (exclusiveSl) {
                // Every document the UI's FIFO could consume (no type or mode filter), all or nothing.
                List<PendingSlDocument> whole = toDocs(raw, sl, domain, null, DocumentSelectionMode.MIXED);
                if (!DocumentReservationRegistry.reserveAllOrNone(key, whole)) continue;
                kept.add(new SlNameWithId(sl.getSlName(), sl.getSlid()));
                keptDocs.put(key, new ArrayList<>(docs.subList(0, needDocs)));
                PendingDocumentRecheck.record(domain, sl.getGlCode(), sl.getSlCode(), ouCode, date, key, whole);
                continue;
            }
            Map<String, List<PendingSlDocument>> reserved = DocumentReservationRegistry.filterAndReserve(Map.of(key, docs));
            List<PendingSlDocument> mine = reserved.get(key);
            if (mine == null || mine.size() < needDocs) {
                if (mine != null) DocumentReservationRegistry.release(mine.stream().map(PendingSlDocument::getDocumentNo).collect(Collectors.toList()));
                continue;
            }
            List<PendingSlDocument> trimmed = new ArrayList<>(mine.subList(0, needDocs));
            if (mine.size() > needDocs) {
                DocumentReservationRegistry.release(mine.subList(needDocs, mine.size()).stream()
                        .map(PendingSlDocument::getDocumentNo).collect(Collectors.toList()));
            }
            kept.add(new SlNameWithId(sl.getSlName(), sl.getSlid()));
            keptDocs.put(key, trimmed);
            PendingDocumentRecheck.record(domain, sl.getGlCode(), sl.getSlCode(), ouCode, date, key, trimmed);
        }
        Log.info("[ApiDocSelector{}] probed {} of {} candidate SL(s), kept {}/{} (docs per SL {}{}).",
                domain.label(), probed, ordered.size(), kept.size(), needSls, needDocs, exclusiveSl ? ", exclusive SL claim" : "");
        if (kept.size() < needSls) {
            throw new SkipException("Skipped due to lack of data: Could not find " + needSls + " " + domain.label() + " SL(s) with "
                    + needDocs + " open document(s) (probed " + probed + " of " + ordered.size() + " sub-ledger(s) via DocumentAdjustments/"
                    + domain.module() + ", docs debit_credit='" + domain.debitCredit() + "'"
                    + (docTypeFilter == null ? "" : ", entype=" + docTypeFilter) + ")."
                    + "\n" + DocumentReservationRegistry.snapshotAsString());
        }
        return new DocumentSelectionResult(kept, keptDocs, 0, probed);
    }

    /**
     * The pending the Select Document popup adjusts against. A document that carries TDS has {@code netOfTDSAmt} below its pending; the TDS
     * (pending minus net) is rounded to a whole rupee by {@code api.pending.tdsRoundoff} (typically up, so the popup offers up to a rupee less than the
     * API's net) and taken off the pending. Any other document offers its pending.
     */
    static BigDecimal effectivePending(PendingDocument d) {
        BigDecimal net = d.getNetOfTDSAmt();
        BigDecimal pending = d.getPending();
        boolean carriesTds = net != null && net.signum() != 0 && net.abs().compareTo(pending.abs()) < 0;
        if (!carriesTds) return pending;
        BigDecimal tds = pending.abs().subtract(net.abs());
        BigDecimal rounded = roundTds(tds, ConfigFactory.getApiConfig().pendingTdsRoundoff());
        BigDecimal offered = pending.abs().subtract(rounded).max(BigDecimal.ZERO);
        return offered.multiply(BigDecimal.valueOf(pending.signum()));
    }

    private static BigDecimal roundTds(BigDecimal tds, String mode) {
        String m = mode == null ? "NONE" : mode.trim().toUpperCase(java.util.Locale.ROOT);
        return switch (m) {
            case "CEILING" -> tds.setScale(0, java.math.RoundingMode.CEILING);
            case "FLOOR" -> tds.setScale(0, java.math.RoundingMode.FLOOR);
            case "HALF_UP" -> tds.setScale(0, java.math.RoundingMode.HALF_UP);
            case "NONE" -> tds;
            default -> throw new IllegalArgumentException("api.pending.tdsRoundoff must be CEILING, FLOOR, HALF_UP or NONE, not '" + mode + "'.");
        };
    }

    private static List<PendingSlDocument> toDocs(List<PendingDocument> raw, SubLedgerLookupResult sl, Domain domain,
                                                  String docTypeFilter, DocumentSelectionMode mode) {
        List<PendingSlDocument> all = new ArrayList<>();
        for (PendingDocument d : raw) {
            if (d.getDocument_No() == null || d.getPending() == null || d.getPending().signum() == 0) continue;
            if (!domain.debitCredit().equalsIgnoreCase(d.getDebit_Credit())) continue;
            if (docTypeFilter != null && !docTypeFilter.equalsIgnoreCase(d.getEntype())) continue;
            PendingSlDocument p = new PendingSlDocument();
            p.setSlName(sl.getSlName());
            p.setSlId(sl.getSlid());
            p.setDocumentNo(d.getDocument_No());
            p.setDocumentDate(d.getDocument_Date() == null ? null : LocalDate.parse(d.getDocument_Date().substring(0, 10)));
            p.setEntype(d.getEntype());
            p.setRefNo(d.getRefNo());
            p.setReferenceSite(d.getRef_Admsite_Name());
            if (d.getAmount() != null && d.getAdjamt() != null && d.getAmount().subtract(d.getAdjamt()).subtract(d.getPending()).abs().compareTo(new BigDecimal("0.005")) > 0) {
                Log.warn("[ApiDocSelector{}] {}: pending {} differs from amount {} - adjusted {} (the Select Document popup may show a different pending).",
                        domain.label(), d.getDocument_No(), d.getPending(), d.getAmount(), d.getAdjamt());
            }
            BigDecimal effective = effectivePending(d);
            boolean useNet = effective.compareTo(d.getPending()) != 0;
            p.setPending(effective);
            if (useNet) {
                Log.info("[ApiDocSelector{}] {} pending {} reduced to net-of-TDS {}", domain.label(), d.getDocument_No(), d.getPending(), effective);
            }
            all.add(p);
        }
        if (mode == null || mode == DocumentSelectionMode.MIXED) return all;
        List<PendingSlDocument> positive = all.stream().filter(x -> x.getPending().compareTo(BigDecimal.ZERO) > 0).collect(Collectors.toList());
        if (mode == DocumentSelectionMode.POSITIVE) return positive;
        return positive.isEmpty() ? all : positive;   // HYBRID
    }
}
