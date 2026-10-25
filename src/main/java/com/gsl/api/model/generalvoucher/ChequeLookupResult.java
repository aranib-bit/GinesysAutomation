package com.gsl.api.model.generalvoucher;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Item of {@code GET Finance/api/cheques?module=generalvoucher&glCode=<bankGl>} — the cheque picker of the Payment Info
 * popup, confirmed via HAR: {@code {"chequeNumber":"0046","bookNumber":1,"chequeDate":null,"bookCode":69619,"glCode":null,
 * "chequeStatus":null}}. The endpoint lists the bank GL's <b>usable</b> cheque leaves.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ChequeLookupResult {
    private String chequeNumber;
    private long bookNumber;
    private String chequeDate;
    /** The cheque book's code — sent as {@code bookCode} on the voucher line. */
    private long bookCode;
    private Long glCode;
    private String chequeStatus;
}
