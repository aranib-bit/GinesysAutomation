package com.gsl.api.model.serviceinvoiceadhoc;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * ServiceInvoice/Save's top-level {@code headerCharges[]} shape — confirmed via HAR to use
 * DIFFERENT field casing than everywhere else charges appear: {@code gLCode}/{@code sLCode}/
 * {@code gSTComponent} (capital mid-word letters), not {@code glCode}/{@code slCode}/
 * {@code gstComponent}. Populated by mapping {@link ServiceChargeDetail} (CalculateServiceCharges'
 * response) into this shape — never reuse that class directly here.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class SaveHeaderCharge {
    private String source;
    private double appAmount;
    private String basis;
    private int chargeCode;
    private String chargeName;
    private double chargeAmount;
    private String formulae;
    private Integer gLCode;
    private String gSTComponent;
    private String includeInTds;
    private String isReverse;
    private String isTax;
    private String operationLevel;
    private double rate;
    private int seq;
    private String sign;
    private Integer sLCode;
    @Builder.Default
    private List<TagSite> tagSites = List.of();
}
