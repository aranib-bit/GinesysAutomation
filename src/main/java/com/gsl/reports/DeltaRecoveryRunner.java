package com.gsl.reports;

import com.aventstack.extentreports.ExtentReports;
import com.aventstack.extentreports.ExtentTest;
import com.aventstack.extentreports.reporter.ExtentSparkReporter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * Standalone crash-recovery tool for R-01.
 *
 * Run manually after a JVM crash to rebuild a human-readable HTML report
 * from the append-only delta file:
 *
 *   java -cp <classpath> com.gsl.reports.DeltaRecoveryRunner reports/live/run_delta.ndjson
 *
 * Output: reports/recovered_report.html
 */
public class DeltaRecoveryRunner {

    public static void main(String[] args) throws IOException {
        if (args.length == 0) {
            System.err.println("Usage: DeltaRecoveryRunner <path/to/run_delta.ndjson>");
            System.exit(1);
        }

        var deltaPath    = Paths.get(args[0]);
        var outputPath   = deltaPath.getParent().getParent().resolve("recovered_report.html").toString();
        var recovery     = new ExtentReports();
        var sparkReporter = new ExtentSparkReporter(outputPath);
        sparkReporter.config().setDocumentTitle("Recovered Test Report");
        recovery.attachReporter(sparkReporter);

        var mapper = new ObjectMapper();
        long[] counts = {0, 0, 0};   // pass, fail, skip

        try (var lines = Files.lines(deltaPath)) {
            lines.filter(line -> !line.isBlank()).forEach(line -> {
                try {
                    JsonNode node   = mapper.readTree(line);
                    String testName = node.path("testName").asText("(unknown)");
                    String status   = node.path("status").asText("UNKNOWN");
                    long   duration = node.path("durationMs").asLong(0);
                    String ts       = node.path("timestamp").asText("");

                    ExtentTest test = recovery.createTest(testName);
                    String detail   = String.format("Recovered | duration: %d ms | recorded: %s", duration, ts);

                    switch (status) {
                        case "PASS" -> { test.pass(detail);    counts[0]++; }
                        case "FAIL" -> { test.fail(detail);    counts[1]++; }
                        case "SKIP" -> { test.skip(detail);    counts[2]++; }
                        default     -> test.info("Status: " + status + " | " + detail);
                    }
                } catch (Exception e) {
                    System.err.println("Skipping malformed line: " + e.getMessage());
                }
            });
        }

        recovery.flush();
        System.out.printf("Recovery report written: %s%n", outputPath);
        System.out.printf("Tests recovered — PASS: %d  FAIL: %d  SKIP: %d%n",
            counts[0], counts[1], counts[2]);
    }
}
