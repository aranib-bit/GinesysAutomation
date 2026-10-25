package com.gsl.utils.dataprovider.source.finance;

import com.gsl.config.ConfigFactory;
import com.gsl.enums.TestDataSourceMode;
import com.gsl.testdata.dto.MergedFinanceGeneralVoucherData;

/**
 * Test-time resolution of a General Voucher Create placeholder, routed by {@code testdata.source}: {@code API} resolves through
 * {@link ApiGeneralVoucherDataSource}, everything else through the DB resolution that already existed.
 */
public final class GeneralVoucherLazyLoader {

    private GeneralVoucherLazyLoader() {}

    private static boolean api() {
        return TestDataSourceMode.from(ConfigFactory.getConfig().testDataSource()) == TestDataSourceMode.API;
    }

    public static MergedFinanceGeneralVoucherData forScenario(MergedFinanceGeneralVoucherData placeholder, String methodName) {
        return api() ? ApiGeneralVoucherDataSource.loadFreshForScenario(placeholder, methodName)
                : DbGeneralVoucherDataSource.loadFreshForScenario(placeholder, methodName);
    }

    public static MergedFinanceGeneralVoucherData forMixedTypes(MergedFinanceGeneralVoucherData placeholder, String methodName) {
        return api() ? ApiGeneralVoucherDataSource.loadFreshForMixedTypes(placeholder, methodName)
                : DbGeneralVoucherDataSource.loadFreshForMixedTypes(placeholder, methodName);
    }
}
