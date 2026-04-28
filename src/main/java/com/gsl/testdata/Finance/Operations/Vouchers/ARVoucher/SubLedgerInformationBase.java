package com.gsl.testdata.Finance.Operations.Vouchers.ARVoucher;

import lombok.Getter;
import lombok.Setter;

/**
 * Holds runtime-resolved fields that must not be scanned by ZeroCell's field mapper.
 * ZeroCell calls getDeclaredFields() on the concrete target class only — inherited
 * fields are invisible to it, so slType never appears as a required Excel column.
 */
public abstract class SubLedgerInformationBase {

    @Getter
    @Setter
    private String slType;
}
