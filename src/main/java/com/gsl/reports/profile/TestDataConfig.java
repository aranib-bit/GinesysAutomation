package com.gsl.reports.profile;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

/**
 * Immutable snapshot of the test-data source and file-path values shown in the Extent Report's
 * Runtime Profile "Test Data Configuration" section (built by
 * {@code RuntimeProfileBuilder#buildTestData}, rendered by
 * {@code ReportConfigRenderer#renderTestDataSection}) and echoed into the dashboard system-info
 * panel by {@code ReportConfigRenderer#renderDashboard}.
 *
 * <p>There is no same-named {@code com.gsl.config} class — {@code source} comes from
 * {@code FrameworkConfig#testDataSource()} and the remaining Excel-file-path fields come from
 * {@code ExcelDataConfig}.
 */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TestDataConfig {
    private String source;
    /** {@code db.available} (yes/no) and, when no, {@code validation.nodb.mode} (API/SKIP). */
    private String dbAvailable;
    private String validationNoDbMode;
    private String masterTestData;
    private String receiptsData;
    private String inventoryGroupData;
    private String inventoryItemData;
    private String arVoucherData;
    private String usersListData;
}
