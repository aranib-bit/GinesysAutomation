package com.gsl.api.model.generalvoucher;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Body of {@code DELETE Finance/api/vouchers/general} — confirmed via HAR:
 * {@code {"empCode":<empCode>,"connectedOUCode":<ouCode>,"voucherCodes":[<vchcode>]}} (note {@code voucherCodes}, not
 * {@code code} as in revert/release). The voucher must be Unposted first.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GeneralVoucherDeleteRequest {
    private long empCode;
    private long connectedOUCode;
    private List<Long> voucherCodes;
}
