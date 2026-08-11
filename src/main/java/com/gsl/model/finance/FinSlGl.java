package com.gsl.model.finance;

import lombok.Data;

/**
 * Maps to finsl_gl — the Sub Ledger → allowed GLs allow-list.
 *
 * <p>Confirmed live: NOT every Sub Ledger is valid against every
 * {@code slapp='Y'} GL — Ginesys enforces an explicit per-pair allow-list
 * here. {@link #code} is this specific (Sub Ledger, GL) pair's own id, used
 * to look up an optional further site narrowing in {@code finsl_gl_site}
 * (see {@link com.gsl.database.dao.finance.FinSlGlSiteDao}).
 */
@Data
public class FinSlGl {

    private long code;
    private long slcode;
    private long glcode;
    private String ext;
}
