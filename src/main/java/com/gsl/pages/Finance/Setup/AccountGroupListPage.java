package com.gsl.pages.Finance.Setup;

import com.gsl.constants.FrameworkConstants;
import com.gsl.driver.DriverManager;
import com.gsl.enums.WaitType;
import com.gsl.pages.enums.Finance.Setup.AccountGroupContextMenu;
import com.gsl.utils.Selenium.ActionUtils;
import com.gsl.utils.Selenium.SeleniumUtils;
import com.gsl.utils.Selenium.WaitUtils;
import org.openqa.selenium.By;
import org.openqa.selenium.Keys;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.interactions.Actions;

public class AccountGroupListPage {

    // ── Static locators ───────────────────────────────────────────────────────

    // Context menu items are modelled by AccountGroupContextMenu enum.

    // Server error popup — "Cannot Save record: Reason: Group Name Already exists."
    // Appears when the server rejects the save (e.g. duplicate name). Must be dismissed.
    private static final By SERVER_ERROR_OK =
            By.xpath("//div[contains(@class,'x-window')]//span[text()='OK']/ancestor::a[1]");

    // Delete confirmation dialog's confirm button. ⚠️ UNVERIFIED — guessed as a standard
    // ExtJS Ext.Msg.confirm-style "Yes" button (same x-window pattern as SERVER_ERROR_OK),
    // matching this app's other confirm dialogs seen this session (e.g. the "already logged
    // in — Yes/No" popup). Confirm the real label/structure on the first live run.
    private static final By DELETE_CONFIRM_YES =
            By.xpath("//div[contains(@class,'x-window')]//span[text()='Yes']/ancestor::a[1]");

    // The inline cell editor input. IMPORTANT CORRECTION (second iteration): an earlier
    // version of this locator scoped to a "x-grid-item-focused" <td>, based on the
    // (also later-corrected) assumption that this class reliably marks the newly-added
    // node's cell. Real automated runs proved that assumption wrong too: ExtJS opens the
    // inline editor IMMEDIATELY after Add (no F2/double-click needed — screenshots confirm
    // the editor is already open with text selected before this code ever presses F2), and
    // once editing starts, DOM keyboard focus moves to the editor's own <input>, so
    // "x-grid-item-focused" is no longer present on the <td> at all (confirmed via
    // NoSuchElementException, not a mere invisibility timeout).
    //
    // The robust fix: ExtJS can only have ONE cell in edit mode anywhere on the page at a
    // time, so the editor input is safe to locate with a plain, unscoped global locator —
    // there is no ambiguity to resolve because there is only ever one live instance.
    private static final By CELL_EDITOR_INPUT =
            By.xpath("//div[contains(@class,'x-tree-cell-editor')]//input[contains(@class,'x-form-field')]");

    // ── Dynamic locators ──────────────────────────────────────────────────────

    private static By nodeLabel(String name) {
        return By.xpath("//span[contains(@class,'x-tree-node-text') and text()='" + name + "']");
    }

    private static By nodeExpander(String name) {
        // ExtJS uses two "collapsed" expander class variants: "x-tree-elbow-plus" for a regular
        // node and "x-tree-elbow-end-plus" for a node that is the last child in its list (DOM
        // confirmed via captured HTML — e.g. "Assets"/"Expense" as the last item in their panel
        // use the "-end-plus" variant). contains(@class,'x-tree-elbow-plus') does NOT match
        // "x-tree-elbow-end-plus" since "-end-" breaks the substring, so matching must be done
        // on "plus" alone (never appears in any non-collapsed elbow class) rather than the
        // full "x-tree-elbow-plus" token.
        return By.xpath(
                "//span[contains(@class,'x-tree-node-text') and text()='" + name + "']" +
                "/preceding-sibling::div[contains(@class,'x-tree-expander') " +
                "and contains(@class,'plus')]");
    }

    // ── Page methods ──────────────────────────────────────────────────────────

    public AccountGroupListPage expandNode(String parentName) {
        By expander = nodeExpander(parentName);
        if (SeleniumUtils.isElementPresent(expander, 2)) {
            SeleniumUtils.click(expander, WaitType.CLICKABLE, "EXPANDER[" + parentName + "]");
            SeleniumUtils.elementDisappear(FrameworkConstants.getLoadingMask(), "LoadingMask");
        }
        return this;
    }

    public AccountGroupListPage clickNode(String nodeName) {
        SeleniumUtils.click(nodeLabel(nodeName), WaitType.CLICKABLE, "NODE[" + nodeName + "]");
        return this;
    }

    public AccountGroupListPage rightClickNode(String nodeName) {
        clickNode(nodeName);
        ActionUtils.rightClick(nodeLabel(nodeName));
        return this;
    }

    public AccountGroupListPage clickContextMenuItem(AccountGroupContextMenu item) {
        SeleniumUtils.click(item.locator(), WaitType.CLICKABLE, "CTX_" + item.name());
        return this;
    }

    /**
     * Types the group name into the newly-added node's inline editor.
     *
     * Real automated runs confirmed ExtJS opens this editor IMMEDIATELY after Add — screenshots
     * captured at the very first line of this method (before any F2 press) already showed the
     * editor open with "New Group" selected. So the primary path is simply: wait for the
     * (page-global-safe, see CELL_EDITOR_INPUT) editor input to appear. F2 is kept only as a
     * defensive fallback for any state where auto-open doesn't happen.
     *
     * Escape guard: if the editor cannot be found by either path, press ESC so the placeholder
     * is not left dangling in the tree for subsequent rows.
     */
    public AccountGroupListPage typeGroupName(String name) {
        if (!SeleniumUtils.isElementPresent(CELL_EDITOR_INPUT, 5)) {
            // Fallback — some states may require an explicit trigger to open the editor
            new Actions(DriverManager.getDriver()).sendKeys(Keys.F2).perform();
        }

        if (!SeleniumUtils.isElementPresent(CELL_EDITOR_INPUT, 3)) {
            new Actions(DriverManager.getDriver()).sendKeys(Keys.ESCAPE).perform();
            throw new IllegalStateException(
                    "x-tree-cell-editor input not found after Add (with F2 fallback). " +
                    "Pressed ESC to cancel pending edit.");
        }

        // ! NOTE: must NOT use ActionUtils.sendKeys(By, ...) here — same problem already fixed
        // once in confirmEntry(): it calls element.clear() before sending keys, and clear()
        // on this ExtJS-managed input appears to fire a blur/change-like event that the cell
        // editor interprets as "commit now", triggering an immediate save with an empty name
        // (confirmed live: "Cannot Save record: The Name field is required").
        //
        // SECOND CORRECTION: Keys.chord(Keys.CONTROL, "a") + name also proved unreliable —
        // confirmed live via screenshot that it produced "New GroupaZZZ Test Verify Group"
        // instead of a clean replace. The literal "a" character was typed instead of
        // triggering the OS/browser "select all" shortcut, meaning Ctrl+A was not reliably
        // recognized as a chord in this Actions.sendKeys(element, ...) call. End → Shift+Home
        // is a more reliable, browser-shortcut-independent way to select all text in a
        // single-line input (pure cursor/selection keys, not an OS-level shortcut), so it's
        // used here instead, followed by an explicit verification that the typed value is
        // exactly correct before ever pressing Enter.
        WebElement editorInput = DriverManager.getDriver().findElement(CELL_EDITOR_INPUT);
        new Actions(DriverManager.getDriver())
                .sendKeys(editorInput, Keys.END)
                .keyDown(Keys.SHIFT).sendKeys(Keys.HOME).keyUp(Keys.SHIFT)
                .sendKeys(name)
                .perform();

        String actualValue = editorInput.getAttribute("value");
        if (!name.equals(actualValue)) {
            throw new IllegalStateException(
                    "Editor value after typing was [" + actualValue + "], expected [" + name +
                    "] — selection/replace did not work as expected.");
        }
        return this;
    }

    /**
     * Commits the typed name by pressing ENTER into the cell editor input, then verifies
     * success by checking the expected name actually appears in the tree — the ground truth
     * for what ExtJS saved. This UI-level check is a fast early signal, not the sole authority:
     * the caller (CreateAccountGroup) always re-verifies against the DB immediately afterward
     * via the (name, parent)-scoped DAO query, which is the true source of truth.
     *
     * If the server rejects the save (e.g. duplicate name), an error popup appears.
     * We dismiss it, press ESC to cancel the phantom node, and throw so the caller can
     * decide to skip this row and continue.
     */
    public AccountGroupListPage confirmEntry(String expectedName) {
        // ! NOTE: must NOT use ActionUtils.sendKeys(By, ...) here — it calls element.clear()
        // before sending keys, which would wipe the just-typed name and commit an empty
        // value, causing ExtJS to reject/revert the edit and leave the editor stuck open.
        DriverManager.getDriver().findElement(CELL_EDITOR_INPUT).sendKeys(Keys.ENTER);

        // Check for server-side rejection popup (safety net — should not appear if DB pre-check is correct)
        if (SeleniumUtils.isElementPresent(SERVER_ERROR_OK, 3)) {
            SeleniumUtils.click(SERVER_ERROR_OK, WaitType.CLICKABLE, "SERVER_ERROR_OK");
            // ESC cancels the phantom node so the tree stays clean
            new Actions(DriverManager.getDriver()).sendKeys(Keys.ESCAPE).perform();
            throw new IllegalStateException("Server rejected group creation — possible duplicate name.");
        }

        // Editor closing (name-agnostic) is the "commit attempt finished" signal
        WaitUtils.untilInvisible(CELL_EDITOR_INPUT);
        SeleniumUtils.elementDisappear(FrameworkConstants.getLoadingMask(), "LoadingMask");

        // Ground truth: a node with the expected (final) name must now be visible — catches
        // the "still says New Group" failure case. Not scoped to a specific parent here (the
        // DB check right after in CreateAccountGroup does that precisely); this is a fast
        // UI-level sanity check only.
        if (!isNodeVisible(expectedName)) {
            throw new IllegalStateException(
                    "Node [" + expectedName + "] not visible in tree after save — rename may have failed.");
        }
        return this;
    }

    public boolean isNodeVisible(String name) {
        return SeleniumUtils.isElementPresent(nodeLabel(name), 2);
    }

    /**
     * Renames an existing node via right-click → Edit Group.
     *
     * ⚠️ UNVERIFIED: assumes "Edit Group" reuses the same inline x-tree-cell-editor
     * mechanism as Add (the same ExtJS CellEditing plugin typically backs both add-new and
     * rename actions on a tree). If the real app instead opens a separate modal form, this
     * method's editor-interaction steps (reusing CELL_EDITOR_INPUT / End+Shift+Home+type)
     * will need to change to target that dialog instead — confirm on the first live run.
     */
    public AccountGroupListPage editGroupName(String oldName, String newName) {
        rightClickNode(oldName);
        clickContextMenuItem(AccountGroupContextMenu.EDIT_GROUP);

        if (!SeleniumUtils.isElementPresent(CELL_EDITOR_INPUT, 5)) {
            new Actions(DriverManager.getDriver()).sendKeys(Keys.F2).perform();
        }
        if (!SeleniumUtils.isElementPresent(CELL_EDITOR_INPUT, 3)) {
            new Actions(DriverManager.getDriver()).sendKeys(Keys.ESCAPE).perform();
            throw new IllegalStateException(
                    "x-tree-cell-editor input not found after Edit Group for [" + oldName + "]. " +
                    "Pressed ESC to cancel pending edit.");
        }

        WebElement editorInput = DriverManager.getDriver().findElement(CELL_EDITOR_INPUT);
        new Actions(DriverManager.getDriver())
                .sendKeys(editorInput, Keys.END)
                .keyDown(Keys.SHIFT).sendKeys(Keys.HOME).keyUp(Keys.SHIFT)
                .sendKeys(newName)
                .perform();

        String actualValue = editorInput.getAttribute("value");
        if (!newName.equals(actualValue)) {
            throw new IllegalStateException(
                    "Editor value after typing was [" + actualValue + "], expected [" + newName +
                    "] — selection/replace did not work as expected.");
        }

        DriverManager.getDriver().findElement(CELL_EDITOR_INPUT).sendKeys(Keys.ENTER);

        if (SeleniumUtils.isElementPresent(SERVER_ERROR_OK, 3)) {
            SeleniumUtils.click(SERVER_ERROR_OK, WaitType.CLICKABLE, "SERVER_ERROR_OK");
            new Actions(DriverManager.getDriver()).sendKeys(Keys.ESCAPE).perform();
            throw new IllegalStateException("Server rejected rename — possible duplicate name.");
        }

        WaitUtils.untilInvisible(CELL_EDITOR_INPUT);
        SeleniumUtils.elementDisappear(FrameworkConstants.getLoadingMask(), "LoadingMask");

        if (!isNodeVisible(newName)) {
            throw new IllegalStateException(
                    "Node [" + newName + "] not visible in tree after rename — edit may have failed.");
        }
        return this;
    }

    /**
     * Deletes an existing node via right-click → Delete → confirm.
     *
     * ⚠️ UNVERIFIED: the confirmation dialog structure/button label (DELETE_CONFIRM_YES)
     * is a best guess based on this app's other confirm dialogs — confirm on first live run.
     */
    public AccountGroupListPage deleteNode(String name) {
        rightClickNode(name);
        clickContextMenuItem(AccountGroupContextMenu.DELETE);

        if (!SeleniumUtils.isElementPresent(DELETE_CONFIRM_YES, 5)) {
            throw new IllegalStateException(
                    "Delete confirmation dialog not found after Delete for [" + name + "].");
        }
        SeleniumUtils.click(DELETE_CONFIRM_YES, WaitType.CLICKABLE, "DELETE_CONFIRM_YES");
        SeleniumUtils.elementDisappear(FrameworkConstants.getLoadingMask(), "LoadingMask");

        if (isNodeVisible(name)) {
            throw new IllegalStateException(
                    "Node [" + name + "] still visible in tree after delete confirmation.");
        }
        return this;
    }
}
