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

    /** FK → {@code finpost.postcode} — the voucher-side (payment/receipt) posting entry being tagged. */
    private long postcode1;   // FK → finpost.postcode (voucher side)

    /** {@code finpost.entcode} of the voucher-side entry, denormalized for direct lookup (see
     *  {@link com.gsl.database.dao.finance.FinTagDao#findByEntCode1}). */
    private long entcode1;

    /** FK → {@code finpost.postcode} — the invoice-side entry this tag settles the voucher against. */
    private long postcode2;   // FK → finpost.postcode (invoice side)

    /** {@code finpost.entcode} of the invoice-side entry. */
    private long entcode2;

    /** FK → {@code finsl.slcode} — the sub-ledger common to both sides of this tag. */
    private long slcode;

    /** Amount of the voucher-side entry adjusted/settled against the invoice-side entry by this row. */
    private BigDecimal amount;

    /** Voucher-side {@code finpost.enttype}, e.g. {@code "REC"} (Receipt). */
    private String enttype1;  // e.g. REC

    /** Invoice-side {@code finpost.enttype}, e.g. {@code "INV"} (Invoice). */
    private String enttype2;  // e.g. INV
}
