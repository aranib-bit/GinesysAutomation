package com.gsl.model.finance;

import lombok.Data;
import org.jdbi.v3.core.mapper.reflect.ColumnName;

import java.math.BigDecimal;
import java.time.LocalDateTime;

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

    /** Primary key. */
    private long postcode;

    /** Owning entry's code (e.g. {@code finvchmain.vchcode} as a string-cast number) — every query must scope by this or {@link #entno} to avoid cross-module reads (see class Javadoc). */
    private long entcode;

    /** Owning entry's document number — voucher number on the voucher-side row, invoice number on the invoice-side row (see class Javadoc). */
    private String entno;

    /** Posting-row type code (e.g. {@code "VMG"} General Voucher main grid, {@code "VMP"} AR/AP voucher main posting, {@code "VDG"} GV detail line) — see {@code VoucherType#mainPostingEntType()}. */
    private String enttype;

    /** Document number carried on this posting row (narration/reference display value). */
    private String docno;

    /** FK → {@code fingl.glcode} — the GL this posting entry hits. */
    private long glcode;

    /** Bug fix (2026-09) — needed to disambiguate two per-line (VDG) rows that share the same
     *  {@code glcode} within one document (e.g. two site-applicable lines both posting to the
     *  same GL account) — a NULL DB value maps to {@code 0} (primitive), matching
     *  {@code FinVchDet.slcode}'s own no-sub-ledger convention. */
    private long slcode;

    /** Debit amount posted to {@link #glcode} by this entry. */
    private BigDecimal damount;

    /** Credit amount posted to {@link #glcode} by this entry. */
    private BigDecimal camount;

    /**
     * Cumulative adjusted amount.
     * Increases with each payment tagged against this entry.
     * Outstanding = camount - adjamt.
     */
    private BigDecimal adjamt;

    /** Narration text for this posting row. */
    private String nartext;

    /** 'N'=never posted, 'U'=unposted (draft), 'P'=posted — see main.v_pending_adj, which
     *  only surfaces release_status='P' rows. */
    @ColumnName("release_status")
    private String releaseStatus;

    /** Round 7 — needed for {@code FinPostValidator}'s new "both or neither" pair check
     *  (mirrors {@code FinVchMain}'s identical pairing/Javadoc). */
    @ColumnName("last_access_time")
    private LocalDateTime lastAccessTime;

    @ColumnName("last_access_ecode")
    private Long lastAccessEcode;

    @ColumnName("release_time")
    private LocalDateTime releaseTime;

    @ColumnName("release_ecode")
    private Long releaseEcode;

    /** Item I (Round 9) — previously asserted only in the DiffEngine/changeset layer. */
    @ColumnName("admou_code")
    private Long admouCode;

    /** Item K (Round 9) — needed for the new cross-table date-consistency check
     *  (every finpost row's entdt must equal finvchmain.vchdt). */
    private LocalDateTime entdt;

    /** Round 23 — previously fetched by every {@code SELECT *} DAO query but silently dropped by
     *  JDBI's bean mapper (no matching field existed), making it invisible to
     *  {@code FinPostValidator}/{@code FinPostDao} despite already being asserted at the
     *  DiffEngine/changeset layer ({@code GeneralVoucherCreateChangeSets}). 'N' (not applicable)
     *  for GV/GRN; a genuine "Y" + populated percentage/period is an AP Voucher concept. */
    @ColumnName("cash_discount_applicable")
    private String cashDiscountApplicable;

    /** Genuinely nullable — populated only when {@link #cashDiscountApplicable} is "Y". */
    @ColumnName("cash_discount_percentage")
    private BigDecimal cashDiscountPercentage;

    /** Genuinely nullable — populated only when {@link #cashDiscountApplicable} is "Y". */
    @ColumnName("cash_discount_period")
    private Integer cashDiscountPeriod;

    /** Round 23 — populated only for a PUBLICAPI-created entry (Ginesys's separate published
     *  partner API); blank for a real UI click OR a WEBAPI-driven entry, since WEBAPI hits the
     *  SAME internal endpoint the web app itself calls (see {@code ApiPlatform.java}) — both show
     *  {@code finvchmain.entry_source = "WEB"}, only PUBLICAPI shows {@code "API"}. */
    @ColumnName("intg_code")
    private String intgCode;
}
