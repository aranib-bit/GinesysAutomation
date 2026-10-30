package com.gsl.api.model.common;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** Body of {@code PUT /vouchers/{ap|ar|general}/revert|release}: the voucher codes plus the acting employee and OU. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VoucherActionRequest {
    private List<Long> code;
    private long empCode;
    private long connectedOUCode;
}
