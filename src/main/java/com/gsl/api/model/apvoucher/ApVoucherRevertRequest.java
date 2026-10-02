package com.gsl.api.model.apvoucher;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * {@code PUT Finance/api/vouchers/ap/revert} request body — shape confirmed via a live HAR
 * capture: {@code {"code":[<vchcode>],"empCode":<empCode>,"connectedOUCode":<ouCode>}}.
 * {@code code} is a list (not a single value) so a future multi-select Revert-from-List-Page call
 * could revert several vouchers in one request — this framework's own callers
 * ({@link com.gsl.api.client.ApVoucherApiClient#revertApVoucher}) only ever populate it with the
 * one target vchcode, mirroring the confirmed single-document capture.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApVoucherRevertRequest {
    private List<Long> code;
    private long empCode;
    private long connectedOUCode;
}
