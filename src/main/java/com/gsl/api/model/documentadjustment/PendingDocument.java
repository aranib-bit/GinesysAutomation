package com.gsl.api.model.documentadjustment;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * One row of {@code GET Finance/api/DocumentAdjustments/{apvoucher|arvoucher}} ({@code result.items[]}) — a document with a pending
 * balance for one sub-ledger. The API twin of a {@code v_pending_adj} row; field names follow the response (captured via HAR).
 *
 * <p>{@code debit_Credit} and the sign of {@code pending} differ by module: AP documents the vendor is owed carry {@code C},
 * AR documents the customer owes carry {@code D} with a positive {@code pending} (see {@code DocumentAdjustmentApiClient}).
 */
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class PendingDocument {
    private String entCode;
    private String document_Owner_Site;
    private long admou_Code;
    private String document_No;
    /** ISO local date-time, e.g. {@code 2022-08-12T00:00:00}. */
    private String document_Date;
    private String refNo;
    private String entype;
    private String entName;
    private long glCode;
    private String glName;
    private long slCode;
    private String slName;
    private String debit_Credit;
    private BigDecimal amount;
    private BigDecimal adjamt;
    private BigDecimal pending;
    /** Pending net of TDS: for a document that carries TDS the Select Document popup shows this, not {@link #pending}. */
    private BigDecimal netOfTDSAmt;
    private String ref_Admsite_Name;
    private long refAdmSiteCode;
    private long postCode;
    private String tdsSource;
    private String tdsName;
    private String tdsSection;
}
