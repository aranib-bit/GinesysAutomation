package com.gsl.api.model.serviceinvoiceadhoc;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** One row from POST /ServiceInvoice/GetServices (called with multiple filters in k6). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ServiceLookupResult {
    @JsonProperty("servicecode")
    private int serviceCode;
    @JsonProperty("servicename")
    private String serviceName;
    @JsonProperty("glcode")
    private Integer glCode;
    @JsonProperty("hsN_SAC_CODE")
    private String hsnSacCode;
    @JsonProperty("gsT_ITC_APPL")
    private String gstItcAppl;
    @JsonProperty("taxcode")
    private Integer taxCode;
}
