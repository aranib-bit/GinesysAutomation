package com.gsl.api.model.serviceinvoiceadhoc;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * The rich per-charge shape returned by CalculateServiceCharges — shared identically (confirmed
 * via HAR) across {@code serviceDetails[].serviceCharges[]}, {@code lineCharges[]}, and
 * {@code headerCharges[]} in that response. Uses {@code includeInTDS} (capital DS) — distinct
 * from the reduced {@code includeInTds} shape used elsewhere (see {@link SimpleServiceCharge}).
 * This class is read-only (response-side); mapped into {@link SimpleServiceCharge}/
 * {@link SaveHeaderCharge} before being embedded in the ServiceInvoice/Save request.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ServiceChargeDetail {
    private Integer headerDocCode;
    private Integer serviceCode;
    private double itemBasicValue;
    private Integer detailDocCode;
    private String detailDocCodeStr;
    private int seq;
    private int chargeCode;
    private String chargeName;
    private double rate;
    private Double originalRate;
    private String basis;
    private String sign;
    private double chargeAmount;
    private double appAmount;
    private String formulae;
    private String operationLevel;
    private String isTax;
    private String source;
    private Double forgnCurrRate;
    private String gstComponent;
    private String isReverse;
    private String includeInCost;
    private int iCodeRecordIndex;
    private int recordIndex;
    private Double roundoffAdjAmount;
    private String isRoundOff;
    private Integer slCode;
    private int glCode;
    private boolean recalculateCharge;
    private boolean factorChanged;
    private String isChangeable;
    private double userDefinedItemRate;
    private Double referenceItemOrgRate;
    private boolean factorNotChangeFromCharge;
    private Integer referenceMainCode;
    private boolean excludeInInvoice;
    private Integer referenceDetailCode;
    private String slName;
    private String glName;
    private String codeInStr;
    private String slApplicable;
    private boolean retainAmountCharge;
    private String referenceMainCode_String;
    private String referenceDetailCode_String;
    private Integer termSequence;
    private String includeInTDS;
    private String includeInTDSInTerm;
    private String siteApplicable;
    private Object tagSites;
    private boolean validTagSites;
    private int code;
    // headerCharges-only extras
    private String iCode;
    private Object rateCal;
    private Object batchSerialCode;
}
