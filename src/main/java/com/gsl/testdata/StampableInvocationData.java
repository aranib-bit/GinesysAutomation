package com.gsl.testdata;

/**
 * An {@link InvocationData} row whose "row N of M" position can be assigned after the rows are built, so every data source stamps its rows
 * through {@code TestDataSource.stampInvocations} instead of each repeating the same loop.
 */
public interface StampableInvocationData extends InvocationData {
    void setInvocationIndex(int invocationIndex);

    void setTotalInvocations(int totalInvocations);
}
