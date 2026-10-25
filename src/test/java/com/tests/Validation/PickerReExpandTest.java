package com.tests.Validation;

import com.gsl.utils.Selenium.SeleniumUtils;
import org.testng.annotations.Test;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/** When a picker that collapsed after typing is re-expanded (run 20261009_142905, AR Sub Ledger). */
public class PickerReExpandTest {

    @Test
    public void collapsedFocusedComboIsReExpanded() {
        assertTrue(SeleniumUtils.shouldReExpandPicker(false, "combobox", "false"));
    }

    @Test
    public void nothingToDoWhenListShownOrFocusElsewhere() {
        assertFalse(SeleniumUtils.shouldReExpandPicker(true, "combobox", "false"));
        assertFalse(SeleniumUtils.shouldReExpandPicker(false, "combobox", "true"));
        assertFalse(SeleniumUtils.shouldReExpandPicker(false, null, "false"));
        assertFalse(SeleniumUtils.shouldReExpandPicker(false, "textbox", null));
    }
}
