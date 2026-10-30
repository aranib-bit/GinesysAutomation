package com.gsl.api.bulk;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gsl.api.core.DataSetupException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/** Where and how a bulk data file for k6 is written: {@code <k6.home>/data/generated/<kind>-<runId>.json}, inside the project, git-ignored. */
public final class BulkDataFiles {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private BulkDataFiles() {}

    public static Path write(String kind, String runId, Map<String, Object> content) {
        try {
            Path dir = Files.createDirectories(K6Scripts.home().resolve("data").resolve("generated"));
            Path target = dir.resolve(kind + "-" + runId + ".json");
            MAPPER.writerWithDefaultPrettyPrinter().writeValue(target.toFile(), content);
            return target;
        } catch (IOException e) {
            throw new DataSetupException("[BulkDataFiles] Cannot write the " + kind + " data file", e);
        }
    }

    /** Number of entries of the array {@code field} in a written file. */
    public static int count(Path file, String field) {
        try {
            return MAPPER.readTree(file.toFile()).get(field).size();
        } catch (IOException e) {
            throw new DataSetupException("[BulkDataFiles] Cannot read back " + file, e);
        }
    }

    /** Best-effort removal of a run artifact that is no longer needed. */
    public static void deleteQuietly(Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException ignored) {
            // a leftover run artifact is git-ignored and harmless
        }
    }
}
