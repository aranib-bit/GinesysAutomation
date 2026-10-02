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

    /** FK → {@code finvchmain.vchcode}, denormalized as text — see {@link com.gsl.database.dao.finance.FinCostTagDao}'s class Javadoc for why every query binds it as a String. */
    private String entcode;      // varchar — FK to finvchmain.vchcode (cast to text)

    /** Voucher/document number of the owning entry. */
    private String entno;

    /** Date of the owning entry. */
    private LocalDateTime entdt;

    /** Entry type of the owning line — e.g. {@code "VDG"} for General Voucher detail lines (see class Javadoc). */
    private String enttype;      // e.g. "VDG" for General Voucher detail lines

    /** FK → {@code fingl.glcode} — the GL line this site allocation belongs to. */
    private long glcode;

    private Long slcode;         // nullable — populated only for Type 3 rows

    /** Debit portion of this line's amount allocated to the site. */
    private BigDecimal damount;

    /** Credit portion of this line's amount allocated to the site. */
    private BigDecimal camount;

    /** Accounting year code. */
    private long ycode;

    /** Surrogate row id. */
    private long code;

    /** FK → {@code finpost.postcode} — shared by every allocation row of the same GL line. */
    private long postcode;       // FK → finpost.postcode

    @ColumnName("admsite_code")
    private long admsiteCode;        // the voucher's Owner Site

    @ColumnName("ref_admsite_code")
    private long refAdmsiteCode;     // the actual allocated Site

    /** Operating Unit code (same {@code admou_code} concept as {@code admsite.admou_code}). */
    @ColumnName("admou_code")
    private long admouCode;
}
