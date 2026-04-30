package com.gsl.database;

import java.util.Map;

/**
 * Builds {@link DbIdQueue} keys from DB metadata values and owns the single
 * source of truth for DB-to-UI operation name mappings.
 *
 * <h3>Key format</h3>
 * {@code "{menuShortcode}.{uiOperationName}"}
 * <pre>
 *   MenuOperationKey.build("FinanceARVoucher", "Add")    → "FinanceARVoucher.Add"
 *   MenuOperationKey.build("FinanceARVoucher", "Modify") → "FinanceARVoucher.Edit"
 *   MenuOperationKey.build("FinanceAPVoucher", "Delete") → "FinanceAPVoucher.Delete"
 * </pre>
 *
 * <h3>DB-to-UI mapping</h3>
 * The DB stores {@code sys_shortcode = "Modify"} for what the UI labels "Edit".
 * All such divergences are listed in {@link #DB_TO_UI}.  Callers always pass the
 * DB value — the mapping is applied transparently inside {@link #build}.
 */
public final class MenuOperationKey {

    /**
     * Maps DB {@code mdappoperation.sys_shortcode} values to the UI-facing label
     * used as the key suffix.  Add an entry here whenever a DB name diverges from
     * its UI label.
     */
    private static final Map<String, String> DB_TO_UI = Map.of(
            "Modify", "Edit"
    );

    private MenuOperationKey() {}

    /**
     * Builds the queue key from a DB menu shortcode and a DB operation shortcode.
     *
     * @param menuShortcode  value of {@code mdmenu.sys_shortcode}, e.g. {@code "FinanceARVoucher"}
     * @param dbOpShortcode  value of {@code mdappoperation.sys_shortcode}, e.g. {@code "Modify"}
     * @return key string, e.g. {@code "FinanceARVoucher.Edit"}
     */
    public static String build(String menuShortcode, String dbOpShortcode) {
        String uiOp = DB_TO_UI.getOrDefault(dbOpShortcode, dbOpShortcode);
        return menuShortcode + "." + uiOp;
    }

    /**
     * Returns the UI-facing label for a DB operation shortcode.
     * {@code "Modify"} → {@code "Edit"}; all others pass through unchanged.
     *
     * @param dbOpShortcode value of {@code mdappoperation.sys_shortcode}
     * @return UI label used as the key suffix
     */
    public static String toUiLabel(String dbOpShortcode) {
        return DB_TO_UI.getOrDefault(dbOpShortcode, dbOpShortcode);
    }
}
