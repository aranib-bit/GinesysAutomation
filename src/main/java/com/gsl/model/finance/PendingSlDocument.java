package com.gsl.model.finance;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Carries one pending AR document row from main.v_pending_adj.
 * Used by {@link com.gsl.database.query.PendingAdjQuery} to return open invoice data
 * for the SELECT_DOCUMENT scenario — the document number is what the UI popup needs to
 * identify which rows to tick.
 */
@Data
public class PendingSlDocument {
    /** Sub-ledger name (used as part of grouping key). */
    private String slName;
    /** Sub-ledger ID / code (slid column in v_pending_adj). Used with slName for uniqueness. */
    private String slId;
    /** Document number from v_pending_adj (e.g. "SIC/2526/00201"). */
    private String documentNo;
    /** Document date. */
    private LocalDate documentDate;
    /** Document type code (e.g. "SIC" = sales invoice credit). */
    private String entype;
    /** Reference number. */
    private String refNo;
    /** Reference site name (ref_admsite_name in the view). */
    private String referenceSite;
    /** Remaining pending amount for this document. */
    private BigDecimal pending;
}
