package com.gsl.api.model.generalvoucher;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * The {@code docScheme} object of a General Voucher body — create sends only {@code {"code":137}}; update (and the
 * rootview) also carry {@code name}, {@code sampleDocNumber} and {@code manual}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class GeneralVoucherDocSchemeRef {
    private long code;
    private String name;
    private String sampleDocNumber;
    private String manual;
}
