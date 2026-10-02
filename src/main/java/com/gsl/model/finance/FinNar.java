package com.gsl.model.finance;

import lombok.Data;

/**
 * Maps to {@code finnar} — GL-scoped master list of registered narration texts.
 *
 * <p>Not a per-voucher table: rows are keyed by ({@link #glcode}, {@link #nartext}), with no FK
 * to {@code finvchmain}. Looked up via {@link com.gsl.database.dao.finance.FinNarDao} to assert
 * that a voucher header's narration is a genuinely registered entry for its GL rather than free
 * text (see {@code FinVchMainBaseValidator}).
 */
@Data
public class FinNar {

    /** Primary key. */
    private long narcode;

    /** Registered narration text — unique per {@link #glcode}. */
    private String nartext;

    /** FK → {@code fingl.glcode} — the GL this narration text is registered against. */
    private long glcode;
}
