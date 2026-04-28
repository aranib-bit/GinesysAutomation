package com.gsl.model.finance;

import lombok.Data;

import java.math.BigDecimal;

/**
 * Maps to finpost — GL Accounting Entries table.
 *
 * SHARED TABLE: used by Finance (AR/AP/Journal) AND Procurement (PI, GRN).
 * All queries must be scoped by entno or postcode to avoid cross-module reads.
 *
 * Two roles in AR Voucher flow:
 *   Voucher-side : entno = vchno        (created when voucher is posted)
 *   Invoice-side : entno = invoiceNo    (pre-existing; adjamt updated on settlement)
 */
@Data
public class FinPost {

    private long postcode;
    private long entcode;
    private String entno;
    private String doctype;
    private String docno;
    private long glcode;
    private BigDecimal damount;
    private BigDecimal camount;

    /**
     * Cumulative adjusted amount.
     * Increases with each payment tagged against this entry.
     * Outstanding = camount - adjamt.
     */
    private BigDecimal adjamt;

    private String narration;
}
