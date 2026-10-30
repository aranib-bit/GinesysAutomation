package com.gsl.api.webapi.model.response.voucher;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.gsl.api.core.ApiEnvelopeBase;
import com.gsl.api.model.generalvoucher.GeneralVoucherListItem;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/** Response of {@code POST /vouchers/generalvouchers}: {@code result.items}. */
@Getter
@Setter
public class GeneralVoucherListResponse extends ApiEnvelopeBase {
    private Result result;

    public List<GeneralVoucherListItem> items() {
        return result == null || result.getItems() == null ? List.of() : result.getItems();
    }

    @Getter
    @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Result {
        private List<GeneralVoucherListItem> items;
    }
}
