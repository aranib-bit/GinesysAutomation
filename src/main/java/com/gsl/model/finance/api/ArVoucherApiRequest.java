package com.gsl.model.finance.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ArVoucherApiRequest {

    private String voucherDate;
    private String reference;
    private String remarks;
    private String cashBankCode;
    private List<ArVoucherSubLedgerLine> subLedgerLines;
}
