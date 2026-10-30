package com.gsl.utils.dataprovider.source.finance;

import com.gsl.aspects.DataLoadScope;
import com.gsl.config.ConfigFactory;
import com.gsl.enums.TestDataSourceMode;

import java.util.function.Supplier;

/**
 * Test-time routing of a Create placeholder to its data source by {@code testdata.source}: {@code API} resolves through the Api source,
 * everything else through the DB resolution that already existed. The single copy of that choice for AP, AR and GV
 * ({@code ApVoucherLazyLoader}, {@code ArVoucherLazyLoader}, {@code GeneralVoucherLazyLoader} are typed entry points over it). The load
 * runs inside a {@link DataLoadScope} so the logging aspect does not paint a data shortfall as a red FAIL.
 */
public final class LazyLoaders {

    private LazyLoaders() {}

    /** True when {@code testdata.source=API}. */
    public static boolean apiMode() {
        return TestDataSourceMode.from(ConfigFactory.getConfig().testDataSource()) == TestDataSourceMode.API;
    }

    /** Runs {@code api} in API mode, {@code db} otherwise, inside a {@link DataLoadScope}. */
    public static <T> T route(Supplier<T> api, Supplier<T> db) {
        return DataLoadScope.run(apiMode() ? api : db);
    }
}
