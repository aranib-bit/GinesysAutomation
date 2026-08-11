package com.gsl.api.model.subledgerothers;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * One entry of {@code SubLedgerOthersCreateRequest.finSLGLs} — the {@code finsl_gl} GL-permission
 * allow-list General Voucher's Type3 combinations depend on. Shape confirmed via HAR
 * ({@code CreateSubLedgerOthers_stage-ideal...har}): {@code {"code":0,"glcode":2,"glname":"Staff
 * Advances","ext":"N","finSLGLSites":[]}}.
 *
 * <p>{@code finSLGLSites} (per-pair Site narrowing, {@code finsl_gl_site}) must carry at least
 * one {@link FinSlGlSiteInfo} whenever the target GL is Site-Applicable ({@code fingl.costapp =
 * 'Y'}) — the server rejects the create with "Site selection is required for the Ledgers - ..."
 * otherwise. It is only left empty when the target GL is NOT Site-Applicable, in which case the
 * created Sub Ledger is usable against {@link #glcode} unconditionally (no site narrowing
 * possible or needed). See {@code DbGeneralVoucherDataSource.resolveType3Site}'s own fallback to
 * {@code fingl_site} for the equivalent read-side logic.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FinSlGlEntry {
    @Builder.Default
    private long code = 0;
    private long glcode;
    private String glname;
    @Builder.Default
    private String ext = "N";
    @Builder.Default
    private List<FinSlGlSiteInfo> finSLGLSites = List.of();
}
