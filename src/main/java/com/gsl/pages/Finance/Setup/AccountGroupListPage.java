package com.gsl.pages.Finance.Setup;

import com.gsl.constants.FrameworkConstants;
import com.gsl.enums.WaitType;
import com.gsl.utils.Selenium.ActionUtils;
import com.gsl.utils.Selenium.SeleniumUtils;
import com.gsl.utils.Selenium.WaitUtils;
import org.openqa.selenium.By;
import org.openqa.selenium.Keys;

public class AccountGroupListPage {

    // ── Static locators ───────────────────────────────────────────────────────

    // Context menu items — visible only after a right-click
    private static final By CTX_ADD        = By.xpath("//span[text()='Add']");
    private static final By CTX_EDIT_GROUP = By.xpath("//span[text()='Edit Group']");

    // After clicking Add, ExtJS inserts a placeholder node with this text
    private static final By NEW_GROUP_PLACEHOLDER =
            By.xpath("//span[contains(@class,'x-tree-node-text') and text()='New Group']");

    // The floating ExtJS CellEditor input — rendered absolutely outside the <td>,
    // visible only while the inline editor is active
    private static final By FLOATING_EDITOR_INPUT =
            By.xpath("//div[contains(@class,'x-editor') and contains(@style,'visibility: visible')]" +
                     "//input[contains(@class,'x-form-field')]");

    // ── Dynamic locators ──────────────────────────────────────────────────────

    // Clickable label span for any named node
    private static By nodeLabel(String name) {
        return By.xpath("//span[contains(@class,'x-tree-node-text') and text()='" + name + "']");
    }

    // Expander triangle (the x-tree-elbow-plus div) for a named node
    private static By nodeExpander(String name) {
        return By.xpath(
                "//span[contains(@class,'x-tree-node-text') and text()='" + name + "']" +
                "/preceding-sibling::div[contains(@class,'x-tree-elbow-plus') " +
                "and contains(@class,'x-tree-expander')]");
    }

    // ── Page methods ──────────────────────────────────────────────────────────

    /**
     * Expands a parent node if it has an unexpanded expander.
     * Safe to call on already-expanded or leaf nodes — does nothing if no expander found.
     */
    public AccountGroupListPage expandNode(String parentName) {
        By expander = nodeExpander(parentName);
        if (SeleniumUtils.isElementPresent(expander, 2)) {
            SeleniumUtils.click(expander, WaitType.CLICKABLE, "EXPANDER[" + parentName + "]");
            SeleniumUtils.elementDisappear(FrameworkConstants.getLoadingMask(), "LoadingMask");
        }
        return this;
    }

    /**
     * Right-clicks the named node to open its context menu.
     */
    public AccountGroupListPage rightClickNode(String nodeName) {
        ActionUtils.rightClick(nodeLabel(nodeName));
        return this;
    }

    /**
     * Clicks "Add" from the context menu.
     * After this, a "New Group" placeholder node appears and the floating editor input becomes active.
     */
    public AccountGroupListPage clickAdd() {
        SeleniumUtils.click(CTX_ADD, WaitType.CLICKABLE, "CTX_ADD");
        return this;
    }

    /**
     * Types the group name into the floating ExtJS CellEditor input.
     * Waits for the "New Group" placeholder to appear first, then waits for the editor input.
     * If the editor did not activate automatically, double-clicks the placeholder to trigger it.
     */
    public AccountGroupListPage typeGroupName(String name) {
        WaitUtils.untilVisible(NEW_GROUP_PLACEHOLDER);

        // Guard: if floating editor did not appear, double-click the placeholder to activate it
        if (!SeleniumUtils.isElementPresent(FLOATING_EDITOR_INPUT, 2)) {
            ActionUtils.doubleClick(NEW_GROUP_PLACEHOLDER);
        }

        WaitUtils.untilVisible(FLOATING_EDITOR_INPUT);
        ActionUtils.sendKeys(FLOATING_EDITOR_INPUT, name);
        return this;
    }

    /**
     * Presses ENTER to commit the typed name.
     * Waits for the placeholder to disappear confirming the commit succeeded.
     */
    public AccountGroupListPage confirmEntry() {
        ActionUtils.sendKeys(FLOATING_EDITOR_INPUT, Keys.ENTER);
        WaitUtils.untilInvisible(NEW_GROUP_PLACEHOLDER);
        SeleniumUtils.elementDisappear(FrameworkConstants.getLoadingMask(), "LoadingMask");
        return this;
    }

    /**
     * Returns true if a node with this exact name is currently visible in the tree.
     */
    public boolean isNodeVisible(String name) {
        return SeleniumUtils.isElementPresent(nodeLabel(name));
    }
}
