package com.gsl.api.model.generalvoucher;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Body of {@code PUT Finance/api/vouchers/general/revert} and {@code PUT Finance/api/vouchers/general/release} — confirmed
 * via HAR: {@code {"code":[<vchcode>],"empCode":<empCode>,"connectedOUCode":<ouCode>}}. {@code code} is a list, so several
 * vouchers can be reverted/released in one call.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GeneralVoucherActionRequest {
    private List<Long> code;
    private long empCode;
    private long connectedOUCode;
}
