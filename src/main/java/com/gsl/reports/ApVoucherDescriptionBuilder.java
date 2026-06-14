package com.gsl.reports;

import com.gsl.testdata.dto.MergedFinanceApVoucherData;

/**
 * Builds a human-readable English sentence describing an AP Voucher test scenario.
 *
 * <p>Parses the compact {@code testcasename} token string set by the DB data source at
 * DataProvider time (e.g. {@code "ADVANCE | [Bank GL] | With TDS"} or
 * {@code "SEL_DOC | [Cash GL] | Vendor(System) | Amt:FULL | Pmt:CASH"}).
 *
 * <p>Sample output:
 * <pre>
 *   "Verify that an AP Voucher is created successfully using Bank Ledger (Advance) with TDS Deduction."
 *   "Verify that an AP Voucher is created successfully using Cash Ledger and a System-defined Vendor
 *    Sub Ledger with Select Document Adjustment and Cash Payment."
 * </pre>
 */
public final class ApVoucherDescriptionBuilder {

    private ApVoucherDescriptionBuilder() {}

    /**
     * Returns a descriptive sentence for the given test data, or {@code null} if
     * the data does not contain enough information to build one.
     */
    public static String build(MergedFinanceApVoucherData data) {
        if (data == null || data.getMasterTestData() == null) return null;
        if (data.getScenarioType() != null) {
            String negDesc = buildNegativeDescription(data.getScenarioType().name());
            if (negDesc != null) return negDesc;
        }
        String tcName = data.getMasterTestData().getTestcasename();
        if (tcName == null || tcName.isBlank()
                || tcName.equalsIgnoreCase("createApVoucherAdvancePayment")
                || tcName.equalsIgnoreCase("createApVoucherDocumentPayment")) return null;
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
            case "BLANK_REFERENCE_ALLOWED"       -> "Verify that two AP Vouchers with no Reference both save successfully (blank reference is allowed).";
            default -> null;
        };
    }

    static String buildFromRichName(String richName) {
        String[] tokens = richName.split("\\s*\\|\\s*");

        String scenario  = tokens.length > 0 ? tokens[0].trim() : "";
        String glType    = extractGlType(tokens);
        String slClass   = extractSlClass(tokens);
        String slCat     = extractSlCategory(tokens);
        String adjPhrase = adjPhrase(scenario);
        String tdsPhrase = extractTdsPhrase(tokens, scenario);
        String amtPhrase = extractAmtPhrase(tokens);
        String pmtPhrase = extractPmtPhrase(tokens);

        StringBuilder sb = new StringBuilder("Verify that an AP Voucher is created successfully");

        if (glType != null) {
            sb.append(" using ").append(glType).append(" Ledger");
        }

        if (isAdvance(scenario)) {
            sb.append(" (Advance Payment)");
            if (!tdsPhrase.isEmpty()) sb.append(" ").append(tdsPhrase);
        } else {
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
        }

        sb.append(".");
        return sb.toString();
    }

    // ── Token extractors ─────────────────────────────────────────────────────

    private static boolean isAdvance(String scenario) {
        return scenario.toUpperCase().startsWith("ADVANCE");
    }

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

    private static String extractSlClass(String[] tokens) {
        for (String t : tokens) {
            if (t.contains("(System)") || t.equalsIgnoreCase("System SL")) return "System";
            if (t.contains("(User)")   || t.equalsIgnoreCase("User SL"))   return "User";
        }
        return null;
    }

    private static String extractSlCategory(String[] tokens) {
        for (String t : tokens) {
            int paren = t.indexOf('(');
            if (paren > 0 && (t.contains("(System)") || t.contains("(User)"))) {
                return t.substring(0, paren).trim();
            }
        }
        return null;
    }

    private static String adjPhrase(String scenario) {
        String s = scenario.toUpperCase();
        if (s.startsWith("SEL_DOC") || s.startsWith("SELECT_DOCUMENT")) return "with Select Document Adjustment";
        if (s.startsWith("FIFO_SETOFF") || s.startsWith("FIFO"))         return "with FIFO Set-Off Adjustment";
        if (s.startsWith("REFERENCE_SITEWISE") || s.startsWith("REF_FIFO")) return "with Reference Sitewise FIFO Set-Off";
        if (s.startsWith("NO_ADJ") || s.startsWith("NO_ADJUSTMENT"))     return "without Adjustment";
        return "";
    }

    private static String extractTdsPhrase(String[] tokens, String scenario) {
        if (!isAdvance(scenario)) return "";
        for (String t : tokens) {
            String u = t.toUpperCase();
            if (u.contains("WITH TDS") || u.contains("WITH_TDS")) return "with TDS Deduction";
            if (u.contains("NO TDS")   || u.contains("NO_TDS"))   return "without TDS Deduction";
        }
        return "";
    }

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

    private static String extractPmtPhrase(String[] tokens) {
        for (String t : tokens) {
            if (t.startsWith("Pmt:")) {
                String val = t.substring(4).trim();
                return switch (val.toUpperCase()) {
                    case "CASH"                       -> "Cash Payment";
                    case "CHEQUE", "CHECK"            -> "Cheque Payment";
                    case "NEFT", "RTGS", "NEFT_RTGS"  -> "NEFT/RTGS Payment";
                    default                           -> val + " Payment";
                };
            }
        }
        return "";
    }
}
