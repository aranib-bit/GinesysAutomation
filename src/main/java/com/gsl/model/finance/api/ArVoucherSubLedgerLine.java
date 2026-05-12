package com.gsl.model.finance.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ArVoucherSubLedgerLine {

    private String     subLedgerCode;
    private String     glCode;
    private BigDecimal creditAmount;
    private String     adjustmentType;
}
