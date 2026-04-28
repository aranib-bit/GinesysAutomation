package com.gsl.reports;

/**
 * Utility that formats a JDBI-style SQL query template with its actual bound
 * parameter values and logs the resulting "effective SQL" to both the Extent
 * Report and the Log4j log file (via {@link ReportLogger}).
 *
 * <p>Usage:
 * <pre>
 *   DbQueryLogger.log(
 *       "SELECT * FROM finvchdet WHERE vchcode = :vchcode",
 *       "vchcode", vchcode);
 *
 *   DbQueryLogger.log(
 *       "SELECT * FROM finvchdet WHERE vchcode = :vchcode AND slcode = :slcode AND glcode = :glcode",
 *       "vchcode", vchcode, "slcode", slcode, "glcode", glcode);
 * </pre>
 *
 * <p>Output logged to Extent Report and log file:
 * <pre>
 *   [DB Query] SELECT * FROM finvchdet WHERE vchcode = 10042
 *   [DB Query] SELECT * FROM finvchdet WHERE vchcode = 10042 AND slcode = 5 AND glcode = 88
 * </pre>
 *
 * <p>Parameter substitution rules:
 * <ul>
 *   <li>{@code null} values are rendered as {@code NULL} (no quotes)</li>
 *   <li>Numeric values ({@code long}, {@code int}, {@link java.math.BigDecimal}, etc.)
 *       are rendered without quotes so the logged query is directly executable</li>
 *   <li>String values are rendered without quotes for readability
 *       (the logged query is for diagnostic purposes, not execution)</li>
 * </ul>
 */
public final class DbQueryLogger {

    private DbQueryLogger() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Resolves and logs an effective SQL query.
     *
     * @param queryTemplate  JDBI-style SQL with {@code :paramName} placeholders
     * @param nameValuePairs alternating parameter name ({@code String}) and value ({@code Object})
     *                       e.g. {@code "vchcode", 10042, "slcode", 5}
     */
    public static void log(String queryTemplate, Object... nameValuePairs) {
        ReportLogger.info("[DB Query] " + resolve(queryTemplate, nameValuePairs));
    }

    // -------------------------------------------------------------------------
    // Internal
    // -------------------------------------------------------------------------

    /**
     * Substitutes {@code :paramName} placeholders in the template with the
     * supplied values.  Uses a word-boundary regex so that a parameter named
     * {@code code} does not accidentally replace the prefix of {@code codeType}.
     */
    private static String resolve(String template, Object[] pairs) {
        if (template == null)           return "(null query)";
        if (pairs == null || pairs.length == 0) return template;

        String result = template;
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            String name  = String.valueOf(pairs[i]);
            Object val   = pairs[i + 1];
            String token = val == null ? "NULL" : val.toString();
            // Replace :paramName followed by a non-word character or end-of-string
            result = result.replaceAll(":" + name + "(?=\\W|$)", token);
        }
        return result;
    }
}
