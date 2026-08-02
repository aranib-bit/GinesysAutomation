package com.gsl.api.model.serviceinvoiceadhoc;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Nested {@code term} object from GetOtherDetails — supplies the real {@code termCode} (its own
 * {@code code}) and {@code tradeGroupCode} used by CalculateServiceCharges' query params; also
 * embedded verbatim in Save's request.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class Term {
    private String name;
    private String extinct;
    private Integer tradeGroupCode;
    private String tradeGroup;
    private String lineLevelIsChangeable;
    private Integer purchaseReturnGlCode;
    private String purchaseReturnGlName;
    private String purchaseReturnLedgerSlApp;
    private String purchaseReturnGlExtinct;
    private Integer purchaseReturnSlCode;
    private String purchaseReturnSlName;
    private String purchaseReturnSlExtinct;
    private Integer purchaseGlCode;
    private String purchaseLedgerSlApp;
    private String purchaseGlName;
    private String purchaseGlExtinct;
    private Integer purchaseSlCode;
    private String purchaseSlName;
    private String purchaseSlExtinct;
    private int code;
}
