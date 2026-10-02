package com.gsl.api.model.apvoucher;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One entry of {@code ApVoucherRevertResponse}'s {@code result} array — shape confirmed via HAR:
 * {@code {"item1":<vchcode>,"item2":"<scheme_docno>","item3":"success","item4":<postcode>}}.
 * The generic {@code item1}/{@code item2}/{@code item3}/{@code item4} field names are kept
 * literal to the confirmed capture (the live API itself returns a tuple-shaped result, not named
 * fields) rather than guessing more descriptive names that might not match a future capture.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ApVoucherRevertResultItem {
    /** The reverted voucher's own {@code finvchmain.vchcode}. */
    private long item1;
    /** The reverted voucher's own {@code scheme_docno}. */
    private String item2;
    /** Literal {@code "success"} on the confirmed HAR capture. */
    private String item3;
    /** The voucher's {@code finpost.postcode} (or similar internal post reference) — confirmed via HAR. */
    private long item4;
}
