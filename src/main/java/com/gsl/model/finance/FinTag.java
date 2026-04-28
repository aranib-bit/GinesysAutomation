package com.gsl.model.finance;

import lombok.Data;

import java.math.BigDecimal;

/**
 * Maps to fintag — Adjustment Tagging bridge table.
 *
 * SHARED TABLE: used by Finance and Procurement modules.
 * Links voucher-side finpost (postcode1) to invoice-side finpost (postcode2).
 *
 * Tag cardinality per voucher category:
 *   FULL_SETTLEMENT / PARTIAL_PAYMENT → 1 row
 *   MULTI_INVOICE                     → N rows (one per invoice)
 *   ADVANCE_PAYMENT                   → 0 rows
 */
@Data
public class FinTag {

    private long postcode1;   // FK → finpost.postcode (voucher side)
    private long entcode1;
    private long postcode2;   // FK → finpost.postcode (invoice side)
    private long entcode2;
    private long slcode;
    private BigDecimal amount;
    private String enttype1;  // e.g. REC
    private String enttype2;  // e.g. INV
}
