package com.gsl.reports;

import com.aventstack.extentreports.ExtentReports;
import com.aventstack.extentreports.ExtentTest;
import com.aventstack.extentreports.MediaEntityBuilder;
import com.gsl.config.ConfigFactory;
import com.gsl.config.DataBaseConfig;
import com.gsl.config.ExcelDataConfig;
import com.gsl.database.DatabaseManager;
import com.gsl.utils.Log;
import io.github.bonigarcia.wdm.WebDriverManager;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
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
        if (!checkUrl(extent)) return false;

        String source = ConfigFactory.getConfig().testDataSource();
        Log.info("[HealthCheck] testdata.source={}", source);

        boolean healthy = checkDatabase(extent);

        if (!"DB".equalsIgnoreCase(source)) {
            boolean excelHealthy = checkExcelFiles(extent);
            healthy = healthy && excelHealthy;
        }
        return healthy;
    }

    private static boolean checkUrl(ExtentReports extent) {
        String rawUrl = ConfigFactory.getConfig().url();
        // Strip fragment — HTTP does not send it to the server
        String connectUrl = rawUrl.contains("#") ? rawUrl.substring(0, rawUrl.indexOf('#')) : rawUrl;
        extent.setSystemInfo("App URL", rawUrl);

        int code = -1;
        String ioError = null;

        try {
            // Bypass SSL certificate validation — staging certs may not be in Java's truststore.
            // We only want to know if the server responds, not validate the cert chain.
            TrustManager[] trustAll = {new X509TrustManager() {
                public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
                public void checkClientTrusted(X509Certificate[] c, String a) {}
                public void checkServerTrusted(X509Certificate[] c, String a) {}
            }};
            SSLContext sc = SSLContext.getInstance("TLS");
            sc.init(null, trustAll, new SecureRandom());

            HttpURLConnection conn = (HttpURLConnection) URI.create(connectUrl).toURL().openConnection();
            if (conn instanceof HttpsURLConnection httpsConn) {
                httpsConn.setSSLSocketFactory(sc.getSocketFactory());
                httpsConn.setHostnameVerifier((host, session) -> true);
            }
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(10_000);
            conn.setReadTimeout(10_000);
            conn.setInstanceFollowRedirects(true);
            code = conn.getResponseCode();
            conn.disconnect();
        } catch (IOException e) {
            ioError = e.getMessage();
        } catch (Exception e) {
            ioError = e.getClass().getSimpleName() + ": " + e.getMessage();
        }

        boolean passed = (ioError == null && code / 100 == 2);

        if (passed) {
            extent.setSystemInfo("Health Check: Application URL", "PASSED — HTTP " + code + " → " + rawUrl);
            Log.info("[HealthCheck] Application URL: PASSED (HTTP {})", code);
            return true;
        }

        // Build the failure label
        String failDetail = (ioError != null)
                ? "FAILED — " + ioError
                : "FAILED — HTTP " + code;
        extent.setSystemInfo("Health Check: Application URL", failDetail + " → " + rawUrl);
        Log.error("[HealthCheck] Application URL: {}", failDetail);

        // Capture a headless screenshot of the failure page and attach it to the report
        String screenshot = captureUrlScreenshot(rawUrl);
        if (screenshot != null) {
            try {
                ExtentTest urlNode = extent.createTest("URL Health Check — " + (ioError != null ? "Connection Error" : "HTTP " + code));
                urlNode.fail(
                        "Application URL returned: " + (ioError != null ? ioError : "HTTP " + code),
                        MediaEntityBuilder.createScreenCaptureFromBase64String(screenshot).build()
                );
            } catch (Exception e) {
                Log.warn("[HealthCheck] Could not attach URL screenshot to report: {}", e.getMessage());
            }
        }

        return false;
    }

    private static String captureUrlScreenshot(String url) {
        WebDriver driver = null;
        try {
            WebDriverManager.chromedriver().setup();
            ChromeOptions opts = new ChromeOptions();
            opts.addArguments(
                    "--headless=new",
                    "--no-sandbox",
                    "--disable-dev-shm-usage",
                    "--ignore-certificate-errors",
                    "--window-size=1366,768"
            );
            driver = new ChromeDriver(opts);
            driver.get(url);
            Thread.sleep(2_000);
            return ((TakesScreenshot) driver).getScreenshotAs(OutputType.BASE64);
        } catch (Exception e) {
            Log.warn("[HealthCheck] Could not capture URL screenshot: {}", e.getMessage());
            return null;
        } finally {
            if (driver != null) {
                try { driver.quit(); } catch (Exception ignored) {}
            }
        }
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
