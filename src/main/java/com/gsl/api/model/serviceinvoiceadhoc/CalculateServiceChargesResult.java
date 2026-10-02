package com.gsl.api.model.serviceinvoiceadhoc;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** Result of POST /ServiceInvoice/CalculateServiceCharges — the live-computed GST/charge breakdown. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class CalculateServiceChargesResult {
    private List<ServiceDetailWithCharges> serviceDetails;
    private List<ServiceChargeDetail> lineCharges;
    private List<ServiceChargeDetail> headerCharges;
    private double totalBasicAmt;
    private double totalGrossAmt;
    private double totalNetAmt;

    /** One entry of {@code serviceDetails[]} — the calculated per-line totals plus that line's own {@code serviceCharges[]}. */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ServiceDetailWithCharges {
        private double chgAmtCalc;
        private double chgamt;
        private double othrChgAmt;
        private double effamt;
        private double netamt;
        private Double roundoffAdjAmount;
        private double taxAmt;
        private boolean isChargeCalculated;
        private int code;
        private int serviceCode;
        private double serviceAmount;
        private double quantity;
        private int recordIndex;
        private int glCode;
        private Integer slCode;
        private List<ServiceChargeDetail> serviceCharges;
        private boolean applyNewItemChg;
        private Integer referenceDetailCode;
        private Integer referenceMainCode;
        private boolean recalculateCharge;
        private String gstItcAppl;
        private Object tagSites;
        private Object tagDocuments;
    }
}
