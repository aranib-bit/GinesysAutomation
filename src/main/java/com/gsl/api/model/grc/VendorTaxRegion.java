package com.gsl.api.model.grc;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Result of GET /GRC/GetGRCVendorTaxRegion — vendor's tax region, needed for correct TDS/GST on the GRC. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class VendorTaxRegion {
    private String taxRegion;
    private boolean interState;
}
