package com.gsl.database;

import com.gsl.model.finance.PendingSlDocument;
import com.gsl.model.finance.SlNameWithId;
import lombok.Value;

import java.util.List;
import java.util.Map;

/**
 * Result of one successful atomic SL+document selection pass.
 *
 * <p><b>Invariant:</b> {@code docsBySlKey.size() == selectedSls.size()} and every SL in
 * {@code selectedSls} has at least one reserved document under its composite key
 * ({@code PendingAdjQuery.compositeKey(slName, slId)}) in {@code docsBySlKey}.
 *
 * <p>Downstream consumers may iterate {@code selectedSls} and look up docs in
 * {@code docsBySlKey} without any missing-key fallback.
 */
@Value
public class DocumentSelectionResult {
    /** SLs that were ultimately retained, in original DB order. Same size as {@code docsBySlKey}. */
    List<SlNameWithId> selectedSls;

    /**
     * Keyed by {@code PendingAdjQuery.compositeKey(slName, slId)}.
     * Guaranteed to contain exactly the keys for every SL in {@code selectedSls},
     * each mapping to a non-empty document list.
     */
    Map<String, List<PendingSlDocument>> docsBySlKey;

    /** Retry rounds consumed before success (0 = first attempt succeeded). */
    int attemptsUsed;

    /** Total SL names over-fetched from DB across all attempts (for diagnostics). */
    int totalOverfetched;
}
