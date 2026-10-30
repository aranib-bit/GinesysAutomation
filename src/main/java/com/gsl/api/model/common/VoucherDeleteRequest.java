package com.gsl.api.model.common;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** Body of {@code DELETE /vouchers/{ap|ar|general}}: the (Unposted) voucher codes plus the acting employee and OU. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VoucherDeleteRequest {
    private long empCode;
    private long connectedOUCode;
    private List<Long> voucherCodes;
}
