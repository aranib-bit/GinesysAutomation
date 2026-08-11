package com.gsl.model.finance;

import lombok.Data;
import org.jdbi.v3.core.mapper.reflect.ColumnName;

/**
 * Maps to finsl_gl_site — an optional further narrowing of valid Sites,
 * scoped to one specific (Sub Ledger, GL) pair ({@link #finslGlCode}, the FK
 * to {@code finsl_gl.code}) rather than the GL alone.
 *
 * <p>Confirmed live: only a minority of finsl_gl pairs have any rows here
 * (14 of many pairs, 47 total rows in a live sample) — most Type 3 GL/Sub
 * Ledger combinations fall back to the broader {@code fingl_site} allow-list
 * (see {@link com.gsl.database.dao.finance.FinGlSiteDao}). When rows DO exist
 * for a pair, they are the *only* valid sites, overriding {@code fingl_site}.
 */
@Data
public class FinSlGlSite {

    private long code;

    @ColumnName("finsl_gl_code")
    private long finslGlCode;

    @ColumnName("admsite_code")
    private long admsiteCode;

    private String ext;
}
