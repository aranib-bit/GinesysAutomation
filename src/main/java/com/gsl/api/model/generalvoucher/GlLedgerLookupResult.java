package com.gsl.api.model.generalvoucher;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Item of {@code GET Finance/api/ledgers?module=generalvoucher…} — the GL lookup of the General Voucher Add form, confirmed
 * via HAR ({@code GVVoucherSave…}). Carries the GL attributes the DB-mode fixtures filter on: {@code type} (A/L/I/E),
 * {@code sourceType} (G general, B bank, C cash), {@code siteApplicable}/{@code subLedgerApplicable} (Y/N) — so
 * Type1 = N/N, Type2 (site only) = Y/N, Type3 (site + sub-ledger) = Y/Y — plus {@code glSiteCount}/{@code slCount}.
 * The attributes are populated for {@code module=generalvoucher} (they are null under {@code module=cheque}).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class GlLedgerLookupResult {
    private long glCode;
    private String glName;
    private String abbreviation;
    private String groupName;
    /** {@code "Y"}/{@code "N"}. */
    private String siteApplicable;
    /** {@code "Y"}/{@code "N"}. */
    private String subLedgerApplicable;
    private String accountNumber;
    /** {@code A}sset / {@code L}iability / {@code I}ncome / {@code E}xpense. */
    private String type;
    private String typeDisplay;
    /** {@code G} general / {@code B} bank / {@code C} cash. */
    private String sourceType;
    private String sourceTypeDisplay;
    private String chequeLabel;
    private int glSiteCount;
    private int slCount;

    public boolean isSiteApplicable()       { return "Y".equalsIgnoreCase(siteApplicable); }
    public boolean isSubLedgerApplicable()  { return "Y".equalsIgnoreCase(subLedgerApplicable); }
}
