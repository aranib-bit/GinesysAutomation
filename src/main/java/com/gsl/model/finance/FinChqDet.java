package com.gsl.model.finance;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * Maps to finchqdet — Cheque Book Detail table (one row per cheque leaf in a cheque book).
 * Finance module only. New in Round 7 — no Java layer existed for this table before.
 *
 * <p>{@code stat} transitions {@code 'N'} (New) &rarr; {@code 'I'} (Issued) the moment a cheque
 * number is actually used on a saved voucher — the check the Round 7/8 cheque-book Level 5
 * validation asserts. {@code vchcode} is populated only once issued (null/blank on a fresh 'N'
 * row).
 */
@Data
public class FinChqDet {

    private long code;          // surrogate PK
    private Long bookcode;      // FK -> finchqmain.bookcode
    private String chqno;

    /** Cheque leaf's own date. */
    private LocalDateTime chqdt;
    private Long vchcode;       // FK -> finvchmain.vchcode, populated once issued
    private String type;
    private String stat;        // 'N' = New, 'I' = Issued

    /** FK → {@code fingl.glcode} — the cash/bank GL this cheque leaf's book is registered under. */
    private Long glcode;
}
