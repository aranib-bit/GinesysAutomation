package com.gsl.utils.dataprovider;

import com.gsl.config.ConfigFactory;
import com.gsl.testdata.Finance.Setup.AccountGroup.AccountGroupLifecycleRow;
import com.gsl.testdata.dto.MergedAccountGroupLifecycleData;
import io.github.sskorol.core.DataSupplier;
import io.github.sskorol.data.XlsxReader;
import one.util.streamex.StreamEx;

import java.util.List;

import static io.github.sskorol.data.TestDataReader.use;

/**
 * DataSupplier for the Account Group Create/Edit/Delete lifecycle test ({@code
 * AccountGroupLifecycle}) — Excel-only, reading every row of the naming-convention sheet at
 * {@code ConfigFactory.getExcelDataConfig().finAccGroupLifecyclePath()}.
 *
 * <p><b>Single-invocation model:</b> {@code flatMap=false} — unlike
 * {@link MergedAccountClassLifecycleDataSupplier} (which emits one parallel invocation per Excel
 * row via {@code flatMap=true}), this emits exactly ONE {@link MergedAccountGroupLifecycleData}
 * carrying every row from the sheet; the consuming test method ({@code
 * AccountGroupLifecycle#accountGroupLifecycle}) loops over {@link
 * MergedAccountGroupLifecycleData#getRows()} itself to drive Create, then Edit, then Delete for
 * the flagged subsets, rather than TestNG invoking the method once per row.
 */
public class MergedAccountGroupLifecycleDataSupplier {

    /**
     * Reads the Account Group lifecycle Excel sheet and wraps every row into a single
     * {@link MergedAccountGroupLifecycleData} — see the class Javadoc's "Single-invocation model".
     */
    @DataSupplier(name = "dataSupplier", flatMap = false)
    public StreamEx<MergedAccountGroupLifecycleData> dataSupplier() {
        List<AccountGroupLifecycleRow> allRows = use(XlsxReader.class)
                .withTarget(AccountGroupLifecycleRow.class)
                .withSource(ConfigFactory.getExcelDataConfig().finAccGroupLifecyclePath())
                .read().toList();

        System.out.println("AccountGroupLifecycle rows loaded: " + allRows.size());

        return StreamEx.of(MergedAccountGroupLifecycleData.builder()
                .rows(allRows)
                .build());
    }
}
