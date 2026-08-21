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

    private long vchcode;

    private String vchno;

    @ColumnName("scheme_docno")
    private String schemeDocno;
    private LocalDate vchdt;
    private String vchtype;
    private String refno;

    /** Voucher narration / remarks. Maps to finvchmain.nartext (varchar 500). */
    private String nartext;
    private long glcode;
    private BigDecimal damount;
    private BigDecimal camount;

    @ColumnName("admsite_code_owner")
    private long admsiteCodeOwner;
    private long ycode;

    @ColumnName("entry_source")
    private String entrySource;      // WEB or API

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
}
