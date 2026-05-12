package com.gsl.database;

import com.gsl.enums.DocumentSelectionMode;
import lombok.Builder;
import lombok.Value;
import org.jdbi.v3.core.Jdbi;

import java.util.List;

/**
 * Immutable request DTO passed to {@link DocumentSelector#selectAtomic(SelectorRequest)}.
 *
 * <p>Carries every input needed to perform a single atomic SL+document selection pass:
 * DB handle, scoping filters ({@code ouCodes}, {@code ownerSiteCodes}), optional
 * classification routing ({@code clsCode} / {@code cmpCode}), pool sizing
 * ({@code subLedgerCount}, {@code documentCount}), and contention-tolerance tunables
 * ({@code overfetchMultiplier}, {@code retryBackoffMs}, {@code mode}).
 *
 * <p>The retry loop upper bound is derived dynamically from the distinct {@code slcode}
 * count in {@code v_pending_adj} (via the appropriate count query) rather than a static
 * config value — so the selector never gives up prematurely in large environments and
 * does not spin excessively in small ones.
 *
 * <p>{@code ouCodes} and {@code ownerSiteCodes} are typed {@code List<Long>} to match
 * the existing {@code PendingAdjQuery} / {@code AdmClsQuery} signatures.
 */
@Value
@Builder
public class SelectorRequest {
    Jdbi jdbi;
    List<Long> ouCodes;
    List<Long> ownerSiteCodes;

    /** Nullable — when set, route SL discovery through {@code AdmClsQuery.findSlNamesWithPendingARByClsCode}. */
    Long clsCode;
    /** Nullable — when set, route SL discovery through {@code AdmClsQuery.findSlNamesWithPendingARByCmpCode}. */
    Integer cmpCode;

    int subLedgerCount;
    int documentCount;

    int overfetchMultiplier;
    long retryBackoffMs;
    DocumentSelectionMode mode;
}
