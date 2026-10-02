package com.gsl.testdata.dto;

import com.gsl.testdata.Finance.Setup.AccountGroup.AccountGroupLifecycleRow;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Whole-sheet test data for the Account Group Create/Edit/Delete lifecycle test ({@code
 * AccountGroupLifecycle}), produced by {@code MergedAccountGroupLifecycleDataSupplier}.
 *
 * <p>Single-invocation model: the supplier emits exactly one instance carrying every row of the
 * naming-convention sheet ({@code ConfigFactory.getExcelDataConfig().finAccGroupLifecyclePath()});
 * the test method loops over {@link #rows} itself to drive Create, then Edit, then Delete for the
 * flagged subsets, rather than TestNG invoking the method once per row.
 */
@Data
@Builder
public class MergedAccountGroupLifecycleData {
    private List<AccountGroupLifecycleRow> rows;
}
