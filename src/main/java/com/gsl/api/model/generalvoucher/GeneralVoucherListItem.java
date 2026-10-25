package com.gsl.api.model.generalvoucher;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One row of {@code POST Finance/api/vouchers/generalvouchers}'s {@code result.items[]} — confirmed via HAR. Note: GV's list has
 * no {@code brsDt} (AR/AP do) and {@code paymentMode} is display text ({@code "Cheque"}), unlike the codes ({@code C}/{@code H})
 * in AR/AP lists. {@code status} is {@code P} (Posted) or {@code U} (Unposted); {@code releasedBy/releasedOn} describe the last
 * release/revert action, so do not read them as "currently posted by".
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class GeneralVoucherListItem {
    /** The voucher's {@code vchcode}. */
    private long code;
    private String voucherNumber;
    private String voucherDate;
    private String ownerSite;
    private String ownerSiteShortName;
    private String cashBankName;
    private String reference;
    private String remarks;
    private String paymentMode;
    private String instrumentNumber;
    private String instrumentDate;
    private String chequeLabel;
    private double paymentAmtCR;
    private double receiptAmtDR;
    private String status;
    private String statusDisplay;
    private String releasedOn;
    private String releasedBy;
    private String createdOn;
    private String createdBy;
    private String lastModifiedOn;
    private String lastModifiedBy;
    private String yearName;
    private String entrySource;

    public boolean isPosted()   { return "P".equalsIgnoreCase(status); }
    public boolean isUnposted() { return "U".equalsIgnoreCase(status); }
}
