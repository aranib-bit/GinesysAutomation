package com.gsl.utils.dataprovider.source.finance;

import com.gsl.testdata.dto.MergedFinanceApVoucherData;

/** Test-time resolution of an AP Voucher Create placeholder (see {@link LazyLoaders}). */
public final class ApVoucherLazyLoader {

    private ApVoucherLazyLoader() {}

    public static MergedFinanceApVoucherData forScenario(MergedFinanceApVoucherData placeholder, String methodName) {
        return LazyLoaders.route(() -> ApiApVoucherDataSource.loadFreshForScenario(placeholder, methodName),
                () -> DbApVoucherDataSource.loadFreshForScenario(placeholder, methodName));
    }
}
