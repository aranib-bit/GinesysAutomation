package com.gsl.model.finance;

import lombok.Data;
import org.jdbi.v3.core.mapper.reflect.ColumnName;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Maps to finvchdet — AR Voucher Detail / Line Item table.
 * Finance module only. One finvchmain → N finvchdet rows.
 */
@Data
public class FinVchDet {

    private long vchcode;       // FK → finvchmain.vchcode

    /** FK → {@code finsl.slcode} — sub-ledger this line posts against; {@code 0} when not sub-ledger applicable (see {@link FinPost#getSlcode()}'s matching convention). */
    private long slcode;

    /** FK → {@code fingl.glcode} — the GL this line posts against. */
    private long glcode;

    /** Reference number for this line. */
    private String refno;

    /** Debit amount for this line. */
    private BigDecimal damount;

    /** Credit amount for this line. */
    private BigDecimal camount;

    /** Cheque number entered for this line, if any. */
    private String chqno;
    private LocalDateTime chqdt;   // PostgreSQL TIMESTAMP → e.g. 2026-04-09 00:00:00.000

    /** Payment-Info Edit scenario needs this to build the "before" {@code PaymentInformation}
     *  (see {@code DbGeneralVoucherEditDataSource#buildPaymentEditTarget}) — previously unmapped
     *  on this typed model even though the {@code drawnon} column exists (the DAO's own
     *  {@code SELECT *} already returns it; this just adds the typed accessor). */
    @ColumnName("drawnon")
    private String drawnOn;

    @ColumnName("payment_mode")
    private String paymentMode;

    @ColumnName("tds_app")
    private String tdsApp;

    /** Narration text for this line item. */
    private String nartext;

    @ColumnName("ref_admsite_name")
    private String refAdmsiteName;

    /**
     * Site-applicability flag denormalized straight onto the detail row —
     * confirmed live (cross-tenant, see FinCostTagValidator) to mirror
     * fingl.costapp for this line at posting time. 'Y' for Type2/Type3
     * General Voucher rows, 'N' for Type1.
     */
    @ColumnName("gl_cc_appl")
    private String glCcAppl;

    @ColumnName("isreversal")
    private String isReversal;

    /** Optimistic-locking version stamp — Round 7's Level 2 "must be unique across this
     *  document's own finvchdet rows" check reads this off the typed row. */
    private long dataversion;

    /** FK → finchqmain.bookcode — populated only when this row's chqno resolves to a
     *  pre-registered (KNOWN) cheque-book entry; {@code null}/0 for a synthetic/unknown cheque
     *  number, per Round 7's cheque-book sub-system design. */
    private Long bookcode;

    /** Item K (Round 9) — needed for the new cross-table date-consistency check (every
     *  finvchdet row's vchdt must equal finvchmain.vchdt). */
    private LocalDateTime vchdt;

    /** {@code true} when this row posts to the Debit side ({@code damount} non-zero) — mirrors
     *  {@code ExpenseIncomeDetail.isDebit()}. Used to decide Dr(Payment)/Cr(Receipt) cheque
     *  behavior — confirmed live that cheque-book participation (bookcode/finchqdet) only ever
     *  happens on the Debit side, regardless of voucher type. */
    public boolean isDebit() {
        return damount != null && damount.signum() != 0;
    }
}
