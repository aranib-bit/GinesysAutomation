package com.gsl.api.model.ledger;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * {@code POST /Ledger/Create?code=0} request body — shape confirmed via HAR (both
 * {@code CreateExpenseNotSiteApplicable...har} and {@code CreateExpenseSiteApplicable...har}
 * capture the identical field set, only values differ). Only the fields
 * {@code LedgerApiClient} actually varies per backfill are exposed as builder params below the
 * blank/zero defaults every real capture sent unchanged (address/contact/tax fields — none of
 * which the Expense/Income Detail combination matrix cares about).
 *
 * <p><b>Business rules baked in here, not left to callers:</b>
 * <ul>
 *   <li>{@link #ext} is hardcoded {@code "N"} — a ledger created extinct silently disappears from
 *       every transaction ledger picker (and from every {@code fingl} query in this framework,
 *       all of which filter {@code ext='N'}), so it must never be caller-settable.</li>
 *   <li>{@link #ledgerSiteInfo} being non-empty IS "Site Applicable" — there is no separate
 *       boolean flag for it. Confirmed across the two Ledger HARs: file 1 (Site not applicable)
 *       sent {@code "ledgerSiteInfo":[]}; file 2 (Site applicable) sent it populated with real
 *       {@code siteCode}s.</li>
 * </ul>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LedgerCreateRequest {
    /** "C" = Cash, "B" = Bank, "G" = General — always "G" for this backfill (see plan scope). */
    @Builder.Default
    private String srctype = "G";
    /** "Y"/"N" — Sub Ledger Applicable. */
    private String slapp;
    /** "Y"/"N" — Site's own cost-center-applicable flag (distinct from Site Applicable itself, which is driven by {@link #ledgerSiteInfo}). */
    private String costapp;
    @Builder.Default
    private String brsamt = "";
    @Builder.Default
    private long glcode = 0;
    private String glname;
    private String abbre;
    /** "A"/"L"/"I"/"E" — see {@link com.gsl.enums.GlAccountType}. */
    private String type;
    @Builder.Default
    private String addr = "";
    @Builder.Default
    private String ctname = "";
    @Builder.Default
    private String stdcode = "";
    @Builder.Default
    private String state = "";
    @Builder.Default
    private String country = "";
    @Builder.Default
    private String district = "";
    @Builder.Default
    private String pin = "";
    @Builder.Default
    private String pH1 = "";
    @Builder.Default
    private String pH2 = "";
    @Builder.Default
    private String pH3 = "";
    @Builder.Default
    private String pH4 = "";
    @Builder.Default
    private String fX1 = "";
    @Builder.Default
    private String fX2 = "";
    @Builder.Default
    private String email = "";
    @Builder.Default
    private String website = "";
    @Builder.Default
    private String cp = "";
    @Builder.Default
    private String lstno = "";
    @Builder.Default
    private String cstno = "";
    @Builder.Default
    private String pan = "";
    @Builder.Default
    private boolean isTagged = false;
    /** Must always be "N" — see class Javadoc. {@code LedgerApiClient} never exposes a way to override this. */
    @Builder.Default
    private String ext = "N";
    @Builder.Default
    private String chqlabel = "";
    @Builder.Default
    private long cmpcode = 0;
    @Builder.Default
    private long glseq = 0;
    @Builder.Default
    private long code = 0;
    private String id;
    @Builder.Default
    private boolean isExt = false;
    @Builder.Default
    private boolean isExtinctHidden = true;
    private LedgerGroupRef grpcode;
    private String tdssrccode;
    private String admsitecodeowner;
    /** Non-empty = "Site Applicable" (see class Javadoc) — {@code []} otherwise. */
    @Builder.Default
    private List<LedgerSiteInfo> ledgerSiteInfo = List.of();
}
