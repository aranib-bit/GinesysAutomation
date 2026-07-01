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

public class MergedFinAccGroupDataSupplier {

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
