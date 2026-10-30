package com.gsl.utils.dataprovider.source;

import java.util.List;

/**
 * Strategy interface for loading test data.
 * Implementations: {@link com.gsl.utils.dataprovider.source.finance.ExcelArVoucherDataSource}
 *                  {@link com.gsl.utils.dataprovider.source.finance.DbArVoucherDataSource}
 */
public interface TestDataSource<T> {
    /** Loads and returns the test-data rows applicable to {@code testMethodName}. */
    List<T> load(String testMethodName);

    /** Assigns every row its "row N of M" position (1-based). Replaces the index/total loop each data source used to repeat. */
    static <R extends com.gsl.testdata.StampableInvocationData> List<R> stampInvocations(List<R> rows) {
        int total = rows.size();
        for (int i = 0; i < total; i++) {
            rows.get(i).setInvocationIndex(i + 1);
            rows.get(i).setTotalInvocations(total);
        }
        return rows;
    }
}
