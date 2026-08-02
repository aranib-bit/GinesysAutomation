package com.gsl.api.model.serviceinvoiceadhoc;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** One row from GET /RT/GetAdmSchemeDocList — the doc scheme this Service Invoice Adhoc will use. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class AdmSchemeDocEntry {
    @JsonProperty("docCode")
    private int docSchemeCode;
    private String docSchemeName;
    private String gstAppl;
}
