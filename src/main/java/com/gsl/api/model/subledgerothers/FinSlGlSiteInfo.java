package com.gsl.api.model.subledgerothers;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One entry of {@code FinSlGlEntry.finSLGLSites} — per-pair Site narrowing for a
 * {@code finsl_gl} row, persisted server-side into {@code finsl_gl_site}
 * ({@code code}, {@code finsl_gl_code}, {@code admsite_code}, {@code ext} — see
 * {@code FinSlGlSite}). The server requires at least one Site here whenever the target GL is
 * Site-Applicable ({@code fingl.costapp = 'Y'}) — leaving this list empty for such a GL is what
 * produces "Site selection is required for the Ledgers - ...".
 *
 * <p>{@code ext} is required — confirmed live: sending {@code siteCode} alone (mirroring
 * {@code LedgerSiteInfo}'s shape) reaches the server but then fails the {@code finsl_gl_site}
 * insert with "null value in column \"ext\" ... violates not-null constraint" ({@code ext} is
 * NOT NULL in that table). Defaults to "N", matching {@link FinSlGlEntry#getExt()}'s own default
 * for the parent {@code finsl_gl} row.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FinSlGlSiteInfo {
    private long siteCode;
    @Builder.Default
    private String ext = "N";
}
