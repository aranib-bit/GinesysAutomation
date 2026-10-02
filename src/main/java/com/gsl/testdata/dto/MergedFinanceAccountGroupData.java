package com.gsl.testdata.dto;

import com.gsl.testdata.Finance.Setup.AccountGroup.AccountGroupRow;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Whole-sheet test data for the Finance Account Group hierarchy creation test ({@code
 * CreateAccountGroup}), produced by {@code MergedFinAccGroupDataSupplier}.
 *
 * <p>Single-invocation model: the supplier emits exactly one instance carrying every (optionally
 * {@code suite.type=smoke}-filtered) row of the group-hierarchy sheet ({@code
 * ConfigFactory.getExcelDataConfig().finAccGroupPath()}); the test method loops over {@link
 * #rows} itself to build the full hierarchy in one pass, rather than TestNG invoking the method
 * once per row.
 */
@Data
@Builder
public class MergedFinanceAccountGroupData {
    private List<AccountGroupRow> rows;
}
