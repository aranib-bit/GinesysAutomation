package com.gsl.reports;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.gsl.utils.Log;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Writes {@code environment.json} into the run folder: where and on what the run executed (target OS,
 * browser builds, session cap). Lets Linux and Windows runs be compared — by a person or by Jenkins —
 * without opening the HTML report or the workbook.
 */
public final class RunEnvironmentFile {

    public static final String FILE_NAME = "environment.json";

    private RunEnvironmentFile() {}

    /** Writes the file into {@code runDir}; logs and returns on any failure, never throws. */
    public static void writeSafely(Path runDir, RunMeta meta) {
        try {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("runId", meta.getRunId());
            m.put("env", meta.getEnv());
            m.put("browser", meta.getBrowser());
            m.put("targetOs", meta.getTargetOs());
            m.put("browserBuilds", meta.getBrowserVersions());
            m.put("versionDrift", meta.getVersionDrift());
            m.put("sessionCap", meta.getSessionCap());
            m.put("suite", meta.getSuiteFile().isBlank() ? meta.getSuiteName() : meta.getSuiteFile());
            m.put("branch", meta.getBranch());
            m.put("machine", meta.getMachine());
            m.put("start", meta.getStart() == null ? "" : meta.getStart().toString());
            m.put("end", meta.getEnd() == null ? "" : meta.getEnd().toString());
            Path file = runDir.resolve(FILE_NAME);
            Files.writeString(file, new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT).writeValueAsString(m));
        } catch (Throwable t) {
            Log.warn("[RunEnvironment] {} not written: {}", FILE_NAME, t.toString());
        }
    }
}
