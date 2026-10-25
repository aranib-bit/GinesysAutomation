package com.gsl.api.model.generalvoucher;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * One Expense/Income Detail line ({@code expenseIncomeDetail[]}) of a General Voucher create/update body and of the
 * rootview response — shape confirmed via HAR ({@code GVVoucherSave…har}, {@code GVVoucherRevertDocPage…har}).
 *
 * <p>The amount goes in {@code expense} <em>or</em> {@code income} (the other stays 0); {@code isReversal} is the
 * string {@code "N"}/{@code "Y"}; payment fields ({@code paymentMode} C/H/R, {@code bookCode}, {@code instrumentNumber},
 * {@code instrumentDate}, {@code chequeLabel}, {@code paymentReference}) are filled on the row that carries the payment.
 * {@code costCenterAllocations} is {@code null} for non-site-applicable GLs.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class GeneralVoucherDetailLine {

    /** {@code 0} for a new line; the existing line code on update. */
    @Builder.Default
    private long code = 0;

    private long glCode;
    private String glName;
    /** {@code "Y"}/{@code "N"} — whether this GL takes a site allocation. */
    private String siteApplicable;
    private String groupName;
    private String referenceNumber;

    /** Non-null only for sub-ledger-applicable GLs. */
    private Long slCode;
    @Builder.Default
    private String slName = "";

    @Builder.Default
    private String ledgerNarration = "";

    /** {@code "N"} / {@code "Y"}. */
    @Builder.Default
    private String isReversal = "N";

    @Builder.Default
    private double expense = 0;
    @Builder.Default
    private double income = 0;

    /** {@code "C"} cheque, {@code "R"} NEFT/RTGS, {@code "H"} cash. */
    @Builder.Default
    private String paymentMode = "C";
    private Long bookCode;
    @Builder.Default
    private String instrumentNumber = "";
    /** ISO date-time string, or null when there is no instrument. */
    private String instrumentDate;
    @Builder.Default
    private String chequeLabel = "";
    @Builder.Default
    private String drawnOn = "";
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String paymentReference;

    private List<CostCenterAllocation> costCenterAllocations;

    /** {@code 0} for a new line; the line's current {@code dataVersion} (from the rootview) on update. */
    @Builder.Default
    private long dataVersion = 0;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Boolean instrumentIgnoreStatus;
}
