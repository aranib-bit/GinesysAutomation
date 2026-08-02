package com.gsl.api.model.serviceinvoiceadhoc;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Nested {@code tdsSource} object from GetOtherDetails — also embedded verbatim in Save's request. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class TdsSource {
    private String tdsSrcName;
    private int cmpCode;
    private String srcType;
    private String srcTypeDisplay;
    private int code;
}
