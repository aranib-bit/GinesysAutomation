package com.gsl.model.adm;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One {@code admdocscheme} row mapped to a voucher menu and enabled for the user's sites, together
 * with the counter state ({@code admlastdoc.lastno}) needed to tell whether the scheme can still
 * generate a document number. Filled by {@code AdmDocSchemeDao#findCandidates}; the derived fields
 * ({@code remaining}, {@code capacity}, ...) are computed by {@code DocSchemeResolver}, not the query.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdmDocSchemeCandidate {

    // ── Columns (bean-mapped by JDBI) ─────────────────────────────────────────
    private Long doccode;
    private String docname;
    /** Digits in the document number; the scheme's cap is {@code 10^width - 1}. */
    private Long width;
    /** First number generated for a fresh counter. */
    private Long starting;
    /** {@code 'Y'} = the user types the number; {@code 'N'} = auto-numbered. */
    private String manual;
    /** {@code 'Y'} = counter resets yearly; {@code 'N'} = never resets; anything else (e.g. monthly) = not tracked here. */
    private String generationInterval;
    /** {@code 'Y'} = extinct (no longer usable). */
    private String ext;
    /** {@code MAX(lastno)} for the current accounting year and OU; null when no counter row exists yet. */
    private Long lastnoYear;
    /** {@code MAX(lastno)} over every year for the OU; used for never-resetting schemes. */
    private Long lastnoAny;

    // ── Derived by DocSchemeResolver ──────────────────────────────────────────
    /** {@code 10^width - 1}; null if width is unknown. */
    private Long capacity;
    /** Last number used under this scheme's reset interval; null if the interval isn't tracked here. */
    private Long lastUsed;
    /** Numbers still available; null when capacity cannot be determined (untracked interval). */
    private Long remaining;
    /** True when the docname is one of the configured dedicated test schemes. */
    private boolean testScheme;

    public boolean isManualScheme() {
        return manual != null && "Y".equalsIgnoreCase(manual.trim());
    }

    public boolean isExtinct() {
        return ext != null && "Y".equalsIgnoreCase(ext.trim());
    }
}
