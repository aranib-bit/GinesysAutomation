package com.gsl.utils.dataprovider;

import com.gsl.config.ConfigFactory;
import com.gsl.testdata.Finance.Setup.AccountGroup.AccountGroupLifecycleRow;
import com.gsl.testdata.dto.MergedAccountGroupLifecycleData;
import io.github.sskorol.core.DataSupplier;
import io.github.sskorol.data.XlsxReader;
import one.util.streamex.StreamEx;

import java.util.List;

import static io.github.sskorol.data.TestDataReader.use;

public class MergedAccountGroupLifecycleDataSupplier {

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
