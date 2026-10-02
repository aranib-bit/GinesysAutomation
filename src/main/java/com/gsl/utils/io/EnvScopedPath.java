package com.gsl.utils.io;

import java.util.function.Predicate;

/**
 * Generic mechanic for resolving a file that lives in a per-environment folder. Knows nothing about
 * which environments exist or what the files mean — callers pass the environment folder, the
 * relative path and an existence check.
 *
 * <p>There is deliberately no fallback to another folder: a missing file is an error naming the
 * environment and the path, so a run can never silently use another environment's data.
 */
public final class EnvScopedPath {

    private EnvScopedPath() {}

    /**
     * Returns {@code <envFolder>/data/<relativePath>} (forward slashes) after confirming it exists.
     *
     * @param envFolder    environment folder relative to the resources root, e.g. {@code env/feature-05}
     * @param relativePath path of the file inside the environment's {@code data} folder, e.g. {@code Finance/ApVoucher.xlsx}
     * @param exists       existence check for a resources-root-relative path
     * @throws IllegalStateException when the file is missing in that environment
     */
    public static String resolve(String envFolder, String relativePath, Predicate<String> exists) {
        String rel = relativePath.replace('\\', '/');
        while (rel.startsWith("/")) {
            rel = rel.substring(1);
        }
        String resolved = envFolder + "/data/" + rel;
        if (!exists.test(resolved)) {
            throw new IllegalStateException("Test-data file '" + rel + "' not found for environment folder '"
                    + envFolder + "' (looked for " + resolved + "). Every environment needs its own copy.");
        }
        return resolved;
    }
}
