package com.gsl.model.finance;

import lombok.Data;
import org.jdbi.v3.core.mapper.reflect.ColumnName;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Maps to fincosttag — the real site-amount-allocation table backing the
 * "Allocate Sites" popup on General Voucher (and other voucher types that
 * reuse the same mechanism).
 *
 * <p>Row shape: one row per (GL line × allocated site), all sharing the same
 * {@link #postcode} (the GL line's finpost posting row). Confirmed live on two
 * independent tenants (idealdb + tenantdb_hsbros):
 * <ul>
 *     <li>{@code enttype = "VDG"} for General Voucher detail lines
 *         (finvchmain.vchtype = 'G'); {@code "VMG"} on the sibling finpost row
 *         is the voucher's Cash/Bank header line, not present here.</li>
 *     <li>{@link #slcode} is populated only for Type 3 rows (Sub Ledger
 *         applicable) — {@code null} for Type 1/2.</li>
 *     <li>{@link #admsiteCode} is the voucher's Owner Site (constant across
 *         every row of that voucher); {@link #refAdmsiteCode} is the actual
 *         allocated Site chosen in the Allocate Sites popup — the real site
 *         key, and the two may legitimately be equal for single-site
 *         tenants.</li>
 * </ul>
 *
 * <p><b>{@code costcode} is intentionally NOT mapped here</b> — confirmed dead
 * across 100k+ rows spanning every entity type on two tenants (always NULL),
 * despite its name suggesting it's the site key.
 */
@Data
public class FinCostTag {

    private String entcode;      // varchar — FK to finvchmain.vchcode (cast to text)
    private String entno;
    private LocalDateTime entdt;
    private String enttype;      // e.g. "VDG" for General Voucher detail lines
    private long glcode;
    private Long slcode;         // nullable — populated only for Type 3 rows
    private BigDecimal damount;
    private BigDecimal camount;
    private long ycode;
    private long code;
    private long postcode;       // FK → finpost.postcode

    @ColumnName("admsite_code")
    private long admsiteCode;        // the voucher's Owner Site

    @ColumnName("ref_admsite_code")
    private long refAdmsiteCode;     // the actual allocated Site

    @ColumnName("admou_code")
    private long admouCode;
}
