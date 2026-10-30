package com.gsl.utils.dataprovider.source.finance;

import com.gsl.testdata.dto.MergedFinanceGeneralVoucherData;

/** Test-time resolution of a General Voucher Create placeholder (see {@link LazyLoaders}). */
public final class GeneralVoucherLazyLoader {

    private GeneralVoucherLazyLoader() {}

    public static MergedFinanceGeneralVoucherData forScenario(MergedFinanceGeneralVoucherData placeholder, String methodName) {
        return LazyLoaders.route(() -> ApiGeneralVoucherDataSource.loadFreshForScenario(placeholder, methodName),
                () -> DbGeneralVoucherDataSource.loadFreshForScenario(placeholder, methodName));
    }

    public static MergedFinanceGeneralVoucherData forMixedTypes(MergedFinanceGeneralVoucherData placeholder, String methodName) {
        return LazyLoaders.route(() -> ApiGeneralVoucherDataSource.loadFreshForMixedTypes(placeholder, methodName),
                () -> DbGeneralVoucherDataSource.loadFreshForMixedTypes(placeholder, methodName));
    }
}
