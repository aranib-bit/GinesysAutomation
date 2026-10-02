package com.gsl.api.model.apvoucher;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * {@code PUT Finance/api/vouchers/ap/revert}'s {status, timestamp, message, result, dataVersion,
 * error, success} envelope — mirrors {@code ApVoucherCreateResponse}'s envelope shape. Confirmed
 * via HAR: {@code {"status":0,"timestamp":"...","message":"","result":[{"item1":<vchcode>,
 * "item2":"<scheme_docno>","item3":"success","item4":<postcode>}],"dataVersion":null,"error":null,
 * "success":true}}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ApVoucherRevertResponse {

    private int status;
    private String timestamp;
    private String message;
    private List<ApVoucherRevertResultItem> result;
    private String dataVersion;
    private String error;
    private boolean success;
}
