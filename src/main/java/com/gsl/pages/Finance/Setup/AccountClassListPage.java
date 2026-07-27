package com.gsl.pages.Finance.Setup;

import com.gsl.enums.WaitType;
import com.gsl.pages.VerifiablePage;
import com.gsl.pages.enums.CommonButton;
import com.gsl.pages.enums.Finance.Setup.AccountClassActionsMenu;
import com.gsl.utils.PageHeaderUtils;
import com.gsl.utils.Selenium.SeleniumUtils;
import org.openqa.selenium.By;

/**
 * Account Class list/grid page (Finance → Setup → Chart of Accounts → Account Class).
 *
 * Deliberately kept separate from the "Add Account Class" popup ({@link AddAccountClassPage}) —
 * the grid + Actions toolbar and the modal form are two distinct UI surfaces with their own
 * lifecycles, unlike {@code AccountGroupListPage} (a tree with no modal at all).
 *
 * ⚠️ UNVERIFIED — the "Actions" dropdown button and the grid-row-by-name locator have no existing
 * precedent anywhere in this codebase (confirmed by repo-wide search); confirm against the real
 * DOM on the first live run.
 */
public class AccountClassListPage implements VerifiablePage {

    private static final String EXPECTED_PAGE_HEADER = "Account Class";

    private static final class Loc {
        static final By ACTIONS_BUTTON = By.xpath("//span[normalize-space()='Actions']");
    }

    private static By rowCell(String className) {
        return By.xpath("//div[contains(@class,'x-grid-item-container')]//div[normalize-space()='"
                + className + "']");
    }

    @Override
    public void verifyOnThisPage() {
        String actual = PageHeaderUtils.getPageHeaderText();
        if (!EXPECTED_PAGE_HEADER.equalsIgnoreCase(actual)) {
            throw new IllegalStateException(
                    "Expected to be on the Account Class list (page header '" + EXPECTED_PAGE_HEADER
                    + "') but the live page header reads '" + actual + "' instead.");
        }
    }

    public AddAccountClassPage clickAdd() {
        SeleniumUtils.click(CommonButton.ADD.locator(), WaitType.CLICKABLE, "ACCOUNTCLASS_ADD");
        return new AddAccountClassPage();
    }

    public AccountClassListPage selectGridRow(String className) {
        SeleniumUtils.click(rowCell(className), WaitType.CLICKABLE, "ACCOUNTCLASS_ROW[" + className + "]");
        return this;
    }

    public boolean isClassNameVisibleInGrid(String className) {
        return SeleniumUtils.isElementPresent(rowCell(className), 3);
    }

    private void openActionsMenu() {
        SeleniumUtils.click(Loc.ACTIONS_BUTTON, WaitType.CLICKABLE, "ACCOUNTCLASS_ACTIONS_BUTTON");
    }

    /** Actions → Edit — opens the selected row for editing in the same modal as Add. */
    public AddAccountClassPage clickEdit() {
        openActionsMenu();
        SeleniumUtils.click(AccountClassActionsMenu.EDIT.locator(), WaitType.CLICKABLE, "ACCOUNTCLASS_ACTIONS_EDIT");
        return new AddAccountClassPage();
    }

    /** Actions → Export to Excel — stays on the list page; caller waits for the download separately. */
    public AccountClassListPage clickExportToExcel() {
        openActionsMenu();
        SeleniumUtils.click(AccountClassActionsMenu.EXPORT_TO_EXCEL.locator(), WaitType.CLICKABLE,
                "ACCOUNTCLASS_ACTIONS_EXPORT");
        return this;
    }
}
