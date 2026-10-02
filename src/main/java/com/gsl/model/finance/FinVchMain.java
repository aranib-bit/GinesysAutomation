package com.gsl.model.finance;

import lombok.Data;
import org.jdbi.v3.core.mapper.reflect.ColumnName;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Maps to finvchmain — AR Voucher Header table.
 * Finance module only (not shared with other modules).
 */
@Data
public class FinVchMain {

    /** Primary key. */
    private long vchcode;

    /** Internal voucher number. */
    private String vchno;

    /** Document number as displayed in the UI/reports (e.g. {@code "VG/00375/26-27"}) — what test code and validators key voucher lookups by. */
    @ColumnName("scheme_docno")
    private String schemeDocno;

    /** Voucher date. */
    private LocalDate vchdt;

    /** Voucher type code — see {@code VoucherType#code()} ({@code 'P'}=AP, {@code 'R'}=AR, {@code 'G'}=General, {@code 'C'}=Contra). */
    private String vchtype;

    /** Header reference number. */
    private String refno;

    /** Voucher narration / remarks. Maps to finvchmain.nartext (varchar 500). */
    private String nartext;

    /** FK → {@code fingl.glcode} — the header's Cash/Bank GL. */
    private long glcode;

    /** Header debit amount — see {@code VoucherType#expectedHeaderAmount} for how this balances against the detail lines per voucher domain. */
    private BigDecimal damount;

    /** Header credit amount — see {@code VoucherType#expectedHeaderAmount} for how this balances against the detail lines per voucher domain. */
    private BigDecimal camount;

    @ColumnName("admsite_code_owner")
    private long admsiteCodeOwner;

    /** Accounting year code. */
    private long ycode;

    @ColumnName("entry_source")
    private String entrySource;      // WEB or API

    /** Document lifecycle status (distinct from {@link #releaseStatus}, which tracks release specifically). */
    private String status;

    @ColumnName("release_status")
    private String releaseStatus;

    @ColumnName("creation_dt")
    private LocalDateTime creationDt;

    /**
     * <b>Confirmed live against {@code idealdb}</b> (Postgres, 172.22.4.4) — see the Edit
     * General Voucher plan's Context section, "Schema realities". {@code time} holds an
     * identical value to {@code creation_dt} on every inspected row (both set at insert) —
     * {@code creationDt} above already covers voucher-creation timestamp, so {@code time}
     * itself is not separately mapped here (nothing in this codebase needs the duplicate).
     *
     * <p>{@code last_access_time}/{@code last_access_ecode} are the real "last modified"
     * columns (not {@code lastmodifiedon}/{@code lastmodifiedby}, which don't exist) —
     * {@code last_access_ecode} is a bigint employee-code FK, not a username string; the UI
     * resolves it to a display name via an employee-master join. {@code release_time}/
     * {@code release_ecode} are the matching pair for release (change together with
     * {@code release_status} only when the document is actually released). {@code ecode} is
     * the original creator's employee code — distinct from {@code last_access_ecode}/
     * {@code release_ecode}.
     */
    @ColumnName("last_access_time")
    private LocalDateTime lastAccessTime;

    @ColumnName("last_access_ecode")
    private long lastAccessEcode;

    @ColumnName("release_time")
    private LocalDateTime releaseTime;

    @ColumnName("release_ecode")
    private long releaseEcode;

    private long ecode;

    /** Items H/I (Round 9) — previously asserted only in the DiffEngine/changeset layer
     *  (`GeneralVoucherCreateChangeSets`); mapped here so the hand-written "Database Validation"
     *  layer and "Actual Results" can display/assert them too. */
    private Long doccode;

    @ColumnName("admou_code")
    private Long admouCode;

    /** {@code 'Y'}/{@code 'N'} — Advance-vs-Document payment mode, populated only for AP Voucher
     *  (General Voucher's own rows must always have this {@code NULL} — see
     *  {@code GeneralVoucherCreateChangeSets}'s own {@code ColumnRule.mandatoryNull("isadvance")}
     *  rule). Mapped here (previously read only in code comments, never queried) so an AP-side DB
     *  claim can target an Advance-mode candidate specifically — see
     *  {@code ApVoucherTagStateClaimSupport#claimOne(Jdbi, ApVoucherTagState, Boolean)}. */
    private String isadvance;
}
