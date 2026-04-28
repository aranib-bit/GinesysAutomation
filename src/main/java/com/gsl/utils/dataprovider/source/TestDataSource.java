package com.gsl.utils.dataprovider.source;

import java.util.List;

/**
 * Strategy interface for loading test data.
 * Implementations: {@link com.gsl.utils.dataprovider.source.finance.ExcelArVoucherDataSource}
 *                  {@link com.gsl.utils.dataprovider.source.finance.DbArVoucherDataSource}
 */
public interface TestDataSource<T> {
    List<T> load(String testMethodName);
}
