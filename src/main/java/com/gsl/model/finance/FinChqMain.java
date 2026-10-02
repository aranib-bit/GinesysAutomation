package com.gsl.model.finance;

import lombok.Data;
import org.jdbi.v3.core.mapper.reflect.ColumnName;

import java.time.LocalDateTime;

/**
 * Maps to finchqmain — Cheque Book Master table (one row per registered cheque book).
 * Finance module only. New in Round 7 — no Java layer existed for this table before.
 *
 * <p>Each book is registered against one cash/bank GL ({@link #glcode}); its individual cheque
 * leaves live in {@link FinChqDet}, one row per {@code chqno}, linked via {@link #bookcode}.
 */
@Data
public class FinChqMain {

    /** Primary key. */
    private long bookcode;      // PK
    private long bookno;
    private LocalDateTime bookdt;

    /** FK → {@code fingl.glcode} — the cash/bank GL this cheque book is registered under. */
    private long glcode;
    private String rem;

    /** Accounting year code the book was registered in. */
    private long ycode;

    /** {@code 'N'} = active; anything else = logically deleted. */
    private String ext;

    /** Employee code of the book's creator. */
    private long ecode;
    private LocalDateTime time;

    @ColumnName("created_by")
    private long createdBy;

    @ColumnName("created_on")
    private LocalDateTime createdOn;

    @ColumnName("last_modified_by")
    private Long lastModifiedBy;

    @ColumnName("last_modified_on")
    private LocalDateTime lastModifiedOn;

    /** Optimistic-locking version stamp. */
    private long dataversion;

    /** FK → {@code admsite.code} — the Site that owns this cheque book. */
    @ColumnName("admsite_code_owner")
    private long admsiteCodeOwner;
}
