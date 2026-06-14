package com.gsl.reports;

import com.gsl.testdata.dto.MergedFinanceArVoucherData;

/**
 * P2: Builds a human-readable English sentence describing the AR Voucher test scenario.
 *
 * <p>The sentence is derived by parsing the compact {@code testcasename} token string
 * (e.g. {@code "SEL_DOC | [Cash GL] | Customer(System) | 1SL-1Doc | Amt:FULL | Grid:EQUAL | Pmt:CASH"})
 * that is set by the DB data source at DataProvider time and is always available even
 * in placeholder objects (before lazy loading).
 *
 * <p>Sample output:
 * <pre>
 *   "Verify that an AR Voucher is created successfully using Cash Ledger and a
 *    System-defined Customer Sub Ledger with Select Document Adjustment and Cash Payment."
 * </pre>
 */
public final class ArVoucherDescriptionBuilder {

    private ArVoucherDescriptionBuilder() {}

    /**
     * Returns a descriptive sentence for the given test data, or {@code null} if
     * the data does not contain enough information to build one.
     */
    public static String build(MergedFinanceArVoucherData data) {
        if (data == null || data.getMasterTestData() == null) return null;
        if (data.getScenarioType() != null) {
            String negDesc = buildNegativeDescription(data.getScenarioType().name());
            if (negDesc != null) return negDesc;
        }
        String tcName = data.getMasterTestData().getTestcasename();
        if (tcName == null || tcName.isBlank()
                || tcName.equalsIgnoreCase("createArVoucher")) return null;
        return buildFromRichName(tcName);
    }

    private static String buildNegativeDescription(String scenarioName) {
        if (scenarioName == null) return null;
        return switch (scenarioName) {
            case "NEG_BLANK_CASHBANK"            -> "Verify that saving without a Cash/Bank GL shows a 'Cash/Bank is required' validation error.";
            case "NEG_BLANK_SUBLEDGER"           -> "Verify that saving with an empty Sub-Ledger grid shows the sub-ledger required validation error.";
            case "NEG_BLANK_MANDATORY_REFERENCE" -> "Verify that saving with a blank mandatory Reference shows a 'Reference No. is required' validation error.";
            case "BOUND_AMOUNT_MIN_INVALID"      -> "Verify that a zero line amount shows an inline validation message and cannot be saved.";
            case "BOUND_AMOUNT_MIN_VALID"        -> "Verify that a minimum line amount (0.01) is accepted and the voucher saves successfully.";
            default -> null;
        };
    }

    /**
     * Parses the " | "-separated compact name and assembles an English sentence.
     * Each token is matched defensively — unknown tokens are silently skipped.
     */
    static String buildFromRichName(String richName) {
        String[] tokens = richName.split("\\s*\\|\\s*");

        String scenario  = tokens.length > 0 ? tokens[0].trim() : "";
        String glType    = extractGlType(tokens);
        String slClass   = extractSlClass(tokens);
        String slCat     = extractSlCategory(tokens);
        String adjPhrase = adjPhrase(scenario);
        String amtPhrase = extractAmtPhrase(tokens);
        String pmtPhrase = extractPmtPhrase(tokens);

        StringBuilder sb = new StringBuilder("Verify that an AR Voucher is created successfully");

        if (glType != null) {
            sb.append(" using ").append(glType).append(" Ledger");
        }

        if (slClass != null || slCat != null) {
            sb.append(" and a");
            if (slClass != null) sb.append(" ").append(slClass).append("-defined");
            if (slCat   != null) sb.append(" ").append(slCat);
            sb.append(" Sub Ledger");
        }

        if (!adjPhrase.isEmpty()) {
            sb.append(" ").append(adjPhrase);
            if (!amtPhrase.isEmpty()) sb.append(" (").append(amtPhrase).append(")");
        }

        if (!pmtPhrase.isEmpty()) {
            sb.append(" and ").append(pmtPhrase);
        }

        sb.append(".");
        return sb.toString();
    }

    // ── Token extractors ─────────────────────────────────────────────────────

    /** Looks for a token like {@code [Cash GL]} or {@code [Bank GL]}. */
    private static String extractGlType(String[] tokens) {
        for (String t : tokens) {
            if (t.startsWith("[") && t.endsWith("]")) {
                String inner = t.substring(1, t.length() - 1).toUpperCase();
                if (inner.contains("CASH")) return "Cash";
                if (inner.contains("BANK")) return "Bank";
            }
        }
        return null;
    }

    /** Extracts "System" or "User" from tokens like {@code Customer(System)} or {@code System SL}. */
    private static String extractSlClass(String[] tokens) {
        for (String t : tokens) {
            if (t.contains("(System)") || t.equalsIgnoreCase("System SL")) return "System";
            if (t.contains("(User)")   || t.equalsIgnoreCase("User SL"))   return "User";
        }
        return null;
    }

    /**
     * Extracts the SL category label from tokens like {@code Customer(System)}.
     * Returns the part before the parenthesis, stripped of whitespace.
     */
    private static String extractSlCategory(String[] tokens) {
        for (String t : tokens) {
            int paren = t.indexOf('(');
            if (paren > 0 && (t.contains("(System)") || t.contains("(User)"))) {
                return t.substring(0, paren).trim();
            }
        }
        return null;
    }

    /** Maps the scenario token to an adjustment description phrase. */
    private static String adjPhrase(String scenario) {
        String s = scenario.toUpperCase();
        if (s.startsWith("SEL_DOC")          || s.startsWith("SELECT_DOCUMENT"))       return "with Select Document Adjustment";
        if (s.startsWith("FIFO_SETOFF")       || s.startsWith("FIFO"))                 return "with FIFO Set-Off Adjustment";
        if (s.startsWith("REFERENCE_SITEWISE")|| s.startsWith("REF_FIFO"))              return "with Reference Sitewise FIFO Set-Off";
        if (s.startsWith("NO_ADJ")            || s.startsWith("NO_ADJUSTMENT"))         return "without Adjustment";
        return "";
    }

    /**
     * Extracts amount description from tokens like {@code Amt:FULL} or {@code Amt:PARTIAL}.
     * Returns empty string when no Amt token is found.
     */
    private static String extractAmtPhrase(String[] tokens) {
        for (String t : tokens) {
            if (t.startsWith("Amt:")) {
                String val = t.substring(4).trim();
                return switch (val.toUpperCase()) {
                    case "FULL"    -> "Full Amount";
                    case "PARTIAL" -> "Partial Amount";
                    default        -> val;
                };
            }
        }
        return "";
    }

    /**
     * Extracts payment description from tokens like {@code Pmt:CASH}, {@code Pmt:CHEQUE}.
     * Returns empty string when no Pmt token is found (= no payment).
     */
    private static String extractPmtPhrase(String[] tokens) {
        for (String t : tokens) {
            if (t.startsWith("Pmt:")) {
                String val = t.substring(4).trim();
                return switch (val.toUpperCase()) {
                    case "CASH"             -> "Cash Payment";
                    case "CHEQUE", "CHECK"  -> "Cheque Payment";
                    case "NEFT", "RTGS", "NEFT_RTGS" -> "NEFT/RTGS Payment";
                    default                 -> val + " Payment";
                };
            }
        }
        return "";
    }
}
