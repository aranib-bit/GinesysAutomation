package com.gsl.utils.Selenium;

import com.gsl.driver.DriverManager;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;

import java.util.ArrayList;
import java.util.List;

/**
 * Locates one body cell of an ExtJS grid by its column, never by counting columns.
 *
 * <p>Every rendered {@code <td>} carries {@code data-columnid}. For a column without a configured
 * {@code itemId} that value equals the header's own {@code id} ({@code gridcolumn-NNNN}); for a column WITH an
 * {@code itemId} (e.g. Ledger Narration's {@code ledgerNarrationCode}) the cell carries the itemId and nothing in
 * the header links the two, so the caller passes it. Counting header divs is wrong because hidden columns render a
 * header but no {@code <td>} (confirmed live, run 20260807_104215, and in the AP Sub-Ledger grid).
 *
 * <p>The header match uses the exact {@code x-column-header} class token: {@code contains(@class,...)} would also
 * match the inner {@code x-column-header-inner} div, whose id no cell carries.
 */
public final class GridCellLocator {

    private GridCellLocator() {}

    /**
     * @param scope      XPath prefix limiting the search to one grid/window ({@code ""} for the whole page)
     * @param rowIndex   0-based record index ({@code table[@data-recordindex]})
     * @param headerText visible header text of the column
     * @param itemId     the column's configured ExtJS itemId, or {@code null}
     */
    public static By cell(String scope, int rowIndex, String headerText, String itemId) {
        return By.xpath(xpath(scope, rowIndex, headerText, itemId));
    }

    /** The XPath behind {@link #cell}; the cell's inner {@code div} holds its text. */
    public static String xpath(String scope, int rowIndex, String headerText, String itemId) {
        return tdXpath(scope, rowIndex, headerText, itemId) + "/div";
    }

    /** The cell's {@code <td>} itself (for clicking / opening its editor). */
    public static String tdXpath(String scope, int rowIndex, String headerText, String itemId) {
        String s = scope == null ? "" : scope;
        String headerId = s + "//span[normalize-space()='" + headerText + "']"
                + "/ancestor::div[contains(concat(' ', normalize-space(@class), ' '), ' x-column-header ')][1]/@id";
        String predicate = "@data-columnid=" + headerId;
        if (itemId != null && !itemId.isBlank()) {
            predicate = "(" + predicate + " or @data-columnid='" + itemId + "')";
        }
        // No positional [1] on the table: another grid on the page (e.g. the list behind a form) can render the same
        // record index; the data-columnid predicate is what picks the right grid's cell.
        return s + "//table[@data-recordindex='" + rowIndex + "']//td[" + predicate + "]";
    }

    /** Column ids the row actually renders, in order - for a "not found" diagnostic. Never throws. */
    public static List<String> renderedColumnIds(String scope, int rowIndex) {
        List<String> ids = new ArrayList<>();
        try {
            By tds = By.xpath("(" + (scope == null ? "" : scope) + "//table[@data-recordindex='" + rowIndex + "'])[1]//td");
            for (WebElement td : DriverManager.getDriver().findElements(tds)) {
                String id = td.getAttribute("data-columnid");
                if (id != null && !id.isBlank()) ids.add(id);
            }
        } catch (RuntimeException ignored) {
            // diagnostic only
        }
        return ids;
    }
}
