package com.gsl.api.model.generalvoucher;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.gsl.api.model.apvoucher.ApVoucherAttachment;
import com.gsl.api.model.arvoucher.CashBankLedgerLookupResult;
import com.gsl.api.model.udf.UdfValueEntry;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Body of {@code POST Finance/api/vouchers/general} (create) and — with {@code code}, {@code docNumber}, {@code dataVersion},
 * per-line {@code code}/{@code dataVersion} filled in and {@code documentStatus="U"} — of {@code PUT Finance/api/vouchers/general}
 * (edit). Shape confirmed via HAR ({@code GVVoucherSave…}, {@code GVVoucherRevertDocPage…}); the UI posts the same
 * structure for both calls, so one class serves both.
 *
 * <p>Differences from AR/AP bodies: objects for {@code ownerSite}/{@code docScheme}/{@code cashBankLedger}, the detail array
 * is {@code expenseIncomeDetail}, and {@code udfDetails} <em>and</em> {@code udfValues} are both sent (same list). Edit
 * requires the voucher to be Unposted first; {@code isAutoReleaseApplied=true} makes Save post it again (it mirrors the
 * profile flag {@code finAutoRelease}).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class GeneralVoucherCreateRequest {

    /** {@code 0} to create; the voucher's code on update. */
    @Builder.Default
    private long code = 0;
    private long empCode;
    private long connectedOUCode;
    private GeneralVoucherOwnerSite ownerSite;
    private GeneralVoucherDocSchemeRef docScheme;
    /** Null for auto-numbered schemes; the existing number on update. */
    private String docNumber;
    private CashBankLedgerLookupResult cashBankLedger;
    /** ISO date-time, e.g. {@code 2026-10-15T00:00:00}. */
    private String voucherDate;
    @Builder.Default
    private int yCode = 0;
    @Builder.Default
    private String reference = "";
    private String remarks;
    /** {@code "N"} for a new voucher, {@code "U"} when editing an Unposted one. */
    @Builder.Default
    private String documentStatus = "N";

    @Builder.Default
    private List<GeneralVoucherDetailLine> expenseIncomeDetail = List.of();

    @JsonProperty("isAutoReleaseApplied")
    @Builder.Default
    private boolean autoReleaseApplied = false;
    @JsonProperty("isReadOnlyInstance")
    @Builder.Default
    private boolean readOnlyInstance = false;
    @JsonProperty("IsMarkedForSaveAndRelease")
    @Builder.Default
    private boolean markedForSaveAndRelease = false;

    /** Update only: the voucher's current header {@code dataVersion} (optimistic concurrency). */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Long dataVersion;

    private String requestId;
    @Builder.Default
    private ApVoucherAttachment attachment = ApVoucherAttachment.builder().build();
    @Builder.Default
    private List<UdfValueEntry> udfDetails = List.of();
    @Builder.Default
    private List<UdfValueEntry> udfValues = List.of();
}
