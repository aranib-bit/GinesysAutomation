package com.gsl.reports;

import com.aventstack.extentreports.ExtentReports;
import com.gsl.config.ConfigFactory;
import com.gsl.config.DataBaseConfig;
import com.gsl.config.ExcelDataConfig;
import com.gsl.database.DatabaseManager;
import com.gsl.utils.Log;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;

public final class PreSuiteHealthCheck {

    private PreSuiteHealthCheck() {}

    /**
     * Runs infrastructure checks before the suite starts and writes results to the
     * report's Environment panel (setSystemInfo) — NOT as test nodes, so the Tests
     * passed/failed count is unaffected.
     *
     * DB connectivity is always checked (used for data validation in every run).
     * Excel file existence is additionally checked when testdata.source=EXCEL.
     *
     * Returns true if all checks pass; false if any check fails.
     * The caller is responsible for aborting the suite when false is returned.
     */
    public static boolean run(ExtentReports extent) {
        String source = ConfigFactory.getConfig().testDataSource();
        Log.info("[HealthCheck] testdata.source={}", source);

        boolean healthy = checkDatabase(extent);

        if (!"DB".equalsIgnoreCase(source)) {
            boolean excelHealthy = checkExcelFiles(extent);
            healthy = healthy && excelHealthy;
        }
        return healthy;
    }

    private static boolean checkDatabase(ExtentReports extent) {
        DataBaseConfig db = ConfigFactory.getDataBaseConfig();

        extent.setSystemInfo("DB Type",     db.databaseType());
        extent.setSystemInfo("DB Host",     db.databaseHost());
        extent.setSystemInfo("DB Port",     db.databasePort());
        extent.setSystemInfo("DB Name",     db.databasename());
        extent.setSystemInfo("DB User",     db.databaseUsername());
        extent.setSystemInfo("DB Pool",     db.PoolName()
                + "  max=" + db.MaximumPoolSize()
                + "  idle=" + db.MinimumIdle());

        try {
            DatabaseManager.getJdbi().withHandle(h ->
                    h.createQuery("SELECT 1").mapTo(Integer.class).one());
            extent.setSystemInfo("Health Check: Database", "PASSED — Connected successfully");
            Log.info("[HealthCheck] Database connectivity: PASSED");
            return true;
        } catch (Throwable e) {
            // Catch Throwable (not just Exception) because DatabaseManager's static
            // initializer failure is wrapped in ExceptionInInitializerError (a java.lang.Error).
            Throwable root = e;
            while (root.getCause() != null) root = root.getCause();
            extent.setSystemInfo("Health Check: Database", "FAILED — " + root.getMessage());
            Log.error("[HealthCheck] Database connectivity: FAILED — {}", root.getMessage());
            return false;
        }
    }

    private static boolean checkExcelFiles(ExtentReports extent) {
        ExcelDataConfig cfg = ConfigFactory.getExcelDataConfig();
        String base = System.getProperty("user.dir") + "/src/test/resources/";

        Map<String, String> files = new LinkedHashMap<>();
        files.put("MasterTestData", cfg.MasterTestData());
        files.put("ArVoucher", cfg.arVoucherPath());

        boolean[] allFound = {true};
        files.forEach((label, relPath) -> {
            File file = new File(base + relPath);
            String fullPath = file.getAbsolutePath();
            if (file.exists()) {
                extent.setSystemInfo("Health Check: " + label, "PASSED — Found: " + fullPath);
                Log.info("[HealthCheck] Excel '{}': FOUND at {}", label, fullPath);
            } else {
                extent.setSystemInfo("Health Check: " + label, "FAILED — File not found: " + fullPath);
                Log.error("[HealthCheck] Excel '{}': NOT FOUND at {}", label, fullPath);
                allFound[0] = false;
            }
        });
        return allFound[0];
    }
}
