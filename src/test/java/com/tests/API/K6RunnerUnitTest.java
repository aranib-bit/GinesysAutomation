package com.tests.API;

import com.gsl.api.bulk.K6Result;
import com.gsl.api.bulk.K6Runner;
import com.gsl.api.bulk.K6Status;
import com.gsl.api.core.DataSetupException;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

/** The k6 launcher against a stub {@code k6} command: exit-code mapping, summary parsing, log unwrapping and the preflight messages, without a real k6. */
public class K6RunnerUnitTest {

    private static final boolean WINDOWS = System.getProperty("os.name").toLowerCase().contains("win");

    private Path home;
    private Path script;
    private K6Runner runner;

    @BeforeClass
    public void setUp() throws IOException {
        Files.createDirectories(Path.of("target"));
        home = Files.createTempDirectory(Path.of("target"), "k6runner-").toAbsolutePath();
        script = Files.createDirectories(home.resolve("scenarios").resolve("backfill")).resolve("stub.js");
        Files.writeString(script, "// stub");
        Files.writeString(Files.createDirectories(home.resolve("erp").resolve("config").resolve("environments")).resolve("t1.json"), "{}");
        runner = new K6Runner(stubBinary().toString(), home);
    }

    /** A fake k6: answers {@code version}, otherwise prints a wrapped log line and a summary and exits with STUB_EXIT. */
    private Path stubBinary() throws IOException {
        if (WINDOWS) {
            Path cmd = home.resolve("k6.cmd");
            Files.writeString(cmd, String.join("\r\n",
                    "@echo off",
                    "if \"%1\"==\"version\" (echo k6 v0.0.0 & exit /b 0)",
                    "echo time=\"2026-10-30T10:00:00+05:30\" level=info msg=\"FillVoucher [VU 1 iter 0] OK type=GV\" source=console",
                    "echo K6_SUMMARY created=%STUB_CREATED% failed=1",
                    "exit /b %STUB_EXIT%"));
            return cmd;
        }
        Path sh = home.resolve("k6");
        Files.writeString(sh, String.join("\n",
                "#!/bin/sh",
                "[ \"$1\" = version ] && { echo k6 v0.0.0; exit 0; }",
                "echo 'time=\"2026-10-30T10:00:00+05:30\" level=info msg=\"FillVoucher [VU 1 iter 0] OK type=GV\" source=console'",
                "echo \"K6_SUMMARY created=$STUB_CREATED failed=1\"",
                "exit $STUB_EXIT"));
        sh.toFile().setExecutable(true);
        return sh;
    }

    private K6Result run(int exit, String tenant) {
        return runner.run(script, Map.of("STUB_EXIT", String.valueOf(exit), "STUB_CREATED", "7", "TENANT", tenant), line -> { });
    }

    @Test
    public void exitCodesMapToTheirMeaning() {
        assertEquals(K6Status.fromExitCode(0), K6Status.PASSED);
        assertEquals(K6Status.fromExitCode(99), K6Status.THRESHOLDS_FAILED);
        assertEquals(K6Status.fromExitCode(102), K6Status.TIMED_OUT);
        assertEquals(K6Status.fromExitCode(107), K6Status.SCRIPT_ERROR);
        assertEquals(K6Status.fromExitCode(1), K6Status.OTHER_FAILURE);
    }

    @Test
    public void aPassingRunReportsItsSummaryAndUnwrappedLines() {
        List<String> lines = new ArrayList<>();
        K6Result result = runner.run(script, Map.of("STUB_EXIT", "0", "STUB_CREATED", "7", "TENANT", "t1"), lines::add);
        assertTrue(result.passed());
        assertEquals(result.getCreated(), 7);
        assertEquals(result.getFailed(), 1);
        assertTrue(lines.contains("FillVoucher [VU 1 iter 0] OK type=GV"), lines.toString());
        assertEquals(result.getOutputTail().size(), lines.size());
    }

    @Test
    public void aCrossedThresholdIsAResultNotAnException() {
        K6Result result = run(99, "t1");
        assertEquals(result.getStatus(), K6Status.THRESHOLDS_FAILED);
        assertFalse(result.passed());
        assertEquals(result.getExitCode(), 99);
    }

    @Test
    public void aMissingScriptIsNamedBeforeAnythingRuns() {
        DataSetupException e = expectThrows(DataSetupException.class,
                () -> runner.run(home.resolve("nope.js"), Map.of("TENANT", "t1"), line -> { }));
        assertTrue(e.getMessage().contains("script not found"), e.getMessage());
    }

    @Test
    public void aMissingTenantFileIsNamedWithItsExpectedPath() {
        DataSetupException e = expectThrows(DataSetupException.class, () -> run(0, "no-such-tenant"));
        assertTrue(e.getMessage().contains("no-such-tenant") && e.getMessage().contains("environments"), e.getMessage());
    }

    @Test
    public void anUnstartableBinaryIsNamed() {
        K6Runner broken = new K6Runner(home.resolve("not-a-k6").toString(), home);
        DataSetupException e = expectThrows(DataSetupException.class, () -> broken.run(script, Map.of("TENANT", "t1"), line -> { }));
        assertTrue(e.getMessage().contains("cannot be started"), e.getMessage());
    }
}
