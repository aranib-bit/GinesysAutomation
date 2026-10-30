package com.gsl.utils.dataprovider.source.finance;

import com.gsl.testdata.dto.MergedFinanceArVoucherData;

/** Test-time resolution of an AR Voucher Create placeholder (see {@link LazyLoaders}). */
public final class ArVoucherLazyLoader {

    private ArVoucherLazyLoader() {}

    public static MergedFinanceArVoucherData forScenario(MergedFinanceArVoucherData placeholder, String methodName) {
        return LazyLoaders.route(() -> ApiArVoucherDataSource.loadFreshForScenario(placeholder, methodName),
                () -> DbArVoucherDataSource.loadFreshForScenario(placeholder, methodName));
    }
}
