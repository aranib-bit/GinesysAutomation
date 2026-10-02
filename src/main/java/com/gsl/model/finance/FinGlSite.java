package com.gsl.model.finance;

import lombok.Data;
import org.jdbi.v3.core.mapper.reflect.ColumnName;

/**
 * Maps to fingl_site — the GL → allowed Sites allow-list.
 *
 * <p>Confirmed live: NOT every active Site is a valid "Allocate Sites" pick
 * for a given GL — Ginesys enforces an explicit per-GL allow-list here. Only
 * relevant for Type 2/Type 3 GLs ({@code fingl.costapp = 'Y'}); Type 1 GLs
 * have no rows in this table at all.
 */
@Data
public class FinGlSite {

    /** FK → {@code fingl.glcode} — the GL this row's allow-list entry belongs to. */
    private long glcode;

    /** FK → {@code admsite.code} — a Site allowed to be picked for {@link #glcode}. */
    @ColumnName("admsite_code")
    private long admsiteCode;

    /** Surrogate row id. */
    private long code;
}
