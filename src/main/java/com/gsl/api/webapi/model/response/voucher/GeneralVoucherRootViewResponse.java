package com.gsl.api.webapi.model.response.voucher;

import com.gsl.api.core.ApiEnvelopeBase;
import com.gsl.api.model.generalvoucher.GeneralVoucherRootView;
import lombok.Getter;
import lombok.Setter;

/** Response of {@code GET /vouchers/generalvouchers/rootview}: the full voucher incl. the data versions needed to edit it. */
@Getter
@Setter
public class GeneralVoucherRootViewResponse extends ApiEnvelopeBase {
    private GeneralVoucherRootView result;
}
