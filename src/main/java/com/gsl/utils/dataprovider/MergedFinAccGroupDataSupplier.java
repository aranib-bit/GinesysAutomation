package com.gsl.utils.dataprovider;

import com.gsl.config.ConfigFactory;
import com.gsl.testdata.Finance.Setup.AccountGroup.AccountGroupRow;
import com.gsl.testdata.dto.MergedFinanceAccountGroupData;
import io.github.sskorol.core.DataSupplier;
import io.github.sskorol.data.XlsxReader;
import one.util.streamex.StreamEx;

import java.util.List;
import java.util.stream.Collectors;

import static io.github.sskorol.data.TestDataReader.use;

/**
 * DataSupplier for the Finance Account Group hierarchy creation test ({@code
 * CreateAccountGroup}) — Excel-only, reading every row of the group-hierarchy sheet at
 * {@code ConfigFactory.getExcelDataConfig().finAccGroupPath()}.
 *
 * <p><b>Single-invocation model:</b> {@code flatMap=false} — like {@link
 * MergedAccountGroupLifecycleDataSupplier}, this emits exactly ONE {@link
 * MergedFinanceAccountGroupData} carrying every (optionally filtered) row; the consuming test
 * method ({@code CreateAccountGroup#CreateAccountGroup}) loops over {@link
 * MergedFinanceAccountGroupData#getRows()} itself to build the full hierarchy in one pass, rather
 * than TestNG invoking the method once per row.
 *
 * <p><b>Suite filtering:</b> when the {@code suite.type} system property is {@code smoke}, only
 * rows whose own {@code AccountGroupRow#getTestSuite()} is {@code smoke} are kept; any other value
 * (including the unset default, {@code regression}) keeps every row.
 */
public class MergedFinAccGroupDataSupplier {

    /**
     * Reads the Account Group hierarchy Excel sheet, filters it by the {@code suite.type} system
     * property (see the class Javadoc's "Suite filtering"), and wraps the result into a single
     * {@link MergedFinanceAccountGroupData}.
     */
    @DataSupplier(name = "dataSupplier", flatMap = false)
    public StreamEx<MergedFinanceAccountGroupData> dataSupplier() {
        List<AccountGroupRow> allRows = use(XlsxReader.class)
                .withTarget(AccountGroupRow.class)
                .withSource(ConfigFactory.getExcelDataConfig().finAccGroupPath())
                .read().toList();

        String activeSuite = System.getProperty("suite.type", "regression").toLowerCase();

        List<AccountGroupRow> filtered;
        if ("smoke".equalsIgnoreCase(activeSuite)) {
            filtered = allRows.stream()
                    .filter(r -> "smoke".equalsIgnoreCase(r.getTestSuite()))
                    .collect(Collectors.toList());
        } else {
            filtered = allRows;
        }

        System.out.println("AccountGroup rows loaded: " + filtered.size() + " (suite=" + activeSuite + ")");

        return StreamEx.of(MergedFinanceAccountGroupData.builder()
                .rows(filtered)
                .build());
    }
}
