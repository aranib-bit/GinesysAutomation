package com.gsl.api.model.transferout;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Result of POST /SI/SearchChallan — a DC eligible for Transfer Out / Service Invoice against it. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class DcSearchResult {
    private String dcCode;
    private String packetBarcode;
}
