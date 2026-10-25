package com.tests.Validation;

import com.gsl.enums.VoucherDomain;
import com.gsl.utils.ui.UiFieldState;
import com.gsl.validation.ui.UiComponentValidator;
import com.gsl.validation.ui.UiComponentValidator.FieldSnapshot;
import com.gsl.validation.ui.UiElementType;
import com.gsl.validation.ui.UiStyleBaseline;
import com.gsl.validation.ui.UiStyleScope;
import com.gsl.validation.ui.UiValidationFailure;
import org.testng.annotations.Test;

import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

// (node-order test is the last method of the class)

/** Browser-free checks of the shared UI-validation building blocks (element type, baseline attributes,
 *  before/after + count logic). Run with {@code -Dtest=UiValidationStandardsTest}. */
public class UiValidationStandardsTest {

    @Test
    public void elementTypeDetectionFromExtJsClasses() {
        assertEquals(UiElementType.detect("input", "text", " x-form-field x-form-text x-form-date-trigger-wrap "), UiElementType.DATE_FIELD);
        assertEquals(UiElementType.detect("input", "text", " x-form-text x-form-arrow-trigger "), UiElementType.DROPDOWN);
        assertEquals(UiElementType.detect("input", "checkbox", ""), UiElementType.CHECKBOX);
        assertEquals(UiElementType.detect("textarea", "", ""), UiElementType.TEXT_AREA);
        assertEquals(UiElementType.detect("button", "", ""), UiElementType.BUTTON);
        assertEquals(UiElementType.detect("input", "text", " x-form-text "), UiElementType.TEXT_FIELD);
        // trigger classes arrive as synthetic tokens folded in from the field container's descendants / ids
        assertEquals(UiElementType.detect("input", "text", " x-form-text x-form-field search-trigger"), UiElementType.SEARCH_FIELD);
        assertEquals(UiElementType.detect("input", "text", " x-form-text x-form-arrow-trigger"), UiElementType.DROPDOWN);
    }

    @Test
    public void elementTypeParseAcceptsHumanAndEnumSpellings() {
        assertEquals(UiElementType.parse("Text Field"), UiElementType.TEXT_FIELD);
        assertEquals(UiElementType.parse("dropdown"), UiElementType.DROPDOWN);
        assertNull(UiElementType.parse("-"));
        assertNull(UiElementType.parse(" "));
        expectThrows(IllegalArgumentException.class, () -> UiElementType.parse("Gizmo"));
    }

    @Test
    public void baselineCarriesBehaviourAttributes() {
        UiStyleScope.set(VoucherDomain.AP);
        assertEquals(UiStyleBaseline.attribute("NARRATION", UiStyleBaseline.ENABLED_BEFORE_FILL), "DISABLED");
        assertEquals(UiStyleBaseline.attribute("REMARKS", UiStyleBaseline.ENABLED_AFTER_FILL), "EDITABLE");
        assertEquals(UiStyleBaseline.attribute("OWNER_SITE", UiStyleBaseline.ELEMENT_TYPE), "Search field");
    }

    @Test
    public void countPairPassesOnExpectedDeltaAndFailsOtherwise() {
        assertTrue(UiComponentValidator.validateCountPair("C", "Record Count", 0, 1, 1).get(0).isPass());
        assertTrue(UiComponentValidator.validateCountPair("C", "Record Count", 1, 1, 1).get(0).isFail());
        assertTrue(UiComponentValidator.validateCountPair("C", "Record Count", null, 1, 1).get(0).isFail());
    }

    @Test
    public void beforeAfterReportsStateAndValueChanges() {
        FieldSnapshot before = new FieldSnapshot(true, UiFieldState.DISABLED, "");
        FieldSnapshot after = new FieldSnapshot(true, UiFieldState.EDITABLE, "Ok");
        List<UiValidationFailure> rows = UiComponentValidator.validateFieldBeforeAfter("REMARKS", "fill", before, after,
                UiFieldState.DISABLED, UiFieldState.EDITABLE, true, null);
        assertTrue(rows.stream().noneMatch(UiValidationFailure::isFail), rows.toString());
        assertTrue(rows.stream().anyMatch(r -> r.property().equals("state change on fill")));

        List<UiValidationFailure> wrong = UiComponentValidator.validateFieldBeforeAfter("REMARKS", "fill", before, before,
                UiFieldState.DISABLED, UiFieldState.EDITABLE, null, null);
        assertTrue(wrong.stream().anyMatch(UiValidationFailure::isFail));
    }

    @Test
    public void subLedgerBalanceMustBeANumberWithCommasAndOptionalCrDr() {
        for (String ok : new String[]{"780,142.00 Cr", "1,234.50 Dr", "500.00", "0.00", "12.00Cr"}) {
            assertTrue(UiComponentValidator.validateBalanceFormat("B", ok).get(0).isPass(), ok);
        }
        for (String bad : new String[]{"", "abc", "780142,00", "1,23.00 Cr", "12.5"}) {
            assertTrue(UiComponentValidator.validateBalanceFormat("B", bad).get(0).isFail(), bad);
        }
    }

    @Test
    public void narrationMustEqualGridLedgerNarration() {
        assertTrue(UiComponentValidator.validateEquals("NARRATION", "equals grid Ledger Narration", "aaa", "aaa").get(0).isPass());
        assertTrue(UiComponentValidator.validateEquals("NARRATION", "equals grid Ledger Narration", "aaa", "aab").get(0).isFail());
    }

    @Test
    public void firstDisplayedMatchSkipsHiddenDuplicatesButAcceptsOffScreenElements() {
        // three "Voucher No.:" copies: two permanently hidden, one displayed (possibly below the fold)
        List<String> items = List.of("hiddenCombo", "hiddenTextfield", "visibleOffScreenDisplay");
        assertEquals(UiComponentValidator.firstMatch(items, i -> i.startsWith("visible")), "visibleOffScreenDisplay");
        assertNull(UiComponentValidator.firstMatch(List.of("a", "b"), i -> false));
    }

    @Test
    public void type1GlChoicePrefersTheProvenGlThenFirstThenNone() {
        com.gsl.model.finance.FinGl a = gl("ASSET");
        com.gsl.model.finance.FinGl adv = gl("Advance Income Tax");
        var choose = (java.util.function.BiFunction<List<com.gsl.model.finance.FinGl>, String, java.util.Optional<com.gsl.model.finance.FinGl>>)
                com.gsl.utils.dataprovider.source.finance.DbGeneralVoucherDataSource::chooseType1Gl;
        assertEquals(choose.apply(List.of(a, adv), "advance income tax").get(), adv);
        assertEquals(choose.apply(List.of(a, adv), "Missing GL").get(), a);
        assertTrue(choose.apply(List.of(), "Advance Income Tax").isEmpty());
    }

    private static com.gsl.model.finance.FinGl gl(String name) {
        com.gsl.model.finance.FinGl g = new com.gsl.model.finance.FinGl();
        g.setGlname(name);
        return g;
    }

    @Test
    public void fieldStateIgnoresTheUnreliableAriaMirrorForInputs() {
        var P = com.gsl.validation.ui.FieldStateProbe.class;
        // GV Remarks after Cash/Bank: natively enabled, container enabled, but aria-disabled stuck at "true"
        assertEquals(com.gsl.validation.ui.FieldStateProbe.decide(
                new com.gsl.validation.ui.FieldStateProbe.Signals("INPUT", false, "true", "false", false, false)),
                UiFieldState.EDITABLE);
        // AP Remarks blank form: natively disabled while aria-disabled says false
        assertEquals(com.gsl.validation.ui.FieldStateProbe.decide(
                new com.gsl.validation.ui.FieldStateProbe.Signals("INPUT", true, "false", "false", false, false)),
                UiFieldState.DISABLED);
        // both agree
        assertEquals(com.gsl.validation.ui.FieldStateProbe.decide(
                new com.gsl.validation.ui.FieldStateProbe.Signals("INPUT", true, "true", "", false, true)),
                UiFieldState.DISABLED);
        // read-only input
        assertEquals(com.gsl.validation.ui.FieldStateProbe.decide(
                new com.gsl.validation.ui.FieldStateProbe.Signals("INPUT", false, "", "true", false, false)),
                UiFieldState.READONLY);
        // non-input (ExtJS anchor button / display field): ARIA is the only signal
        assertEquals(com.gsl.validation.ui.FieldStateProbe.decide(
                new com.gsl.validation.ui.FieldStateProbe.Signals("A", false, "true", "", false, false)),
                UiFieldState.DISABLED);
    }

    @Test
    public void labelAndMarkerResultsFoldIntoTheirFieldsGroup() {
        UiStyleScope.set(VoucherDomain.GV);
        var pass = UiValidationFailure.Status.PASS;
        List<UiValidationFailure> rs = List.of(
                new UiValidationFailure("OWNER_SITE", "label text", "Owner Site:", "Owner Site:", pass),
                new UiValidationFailure("OWNER_SITE_LABEL", "font-size", "12px", "12px", pass),
                new UiValidationFailure("OWNER_SITE_UI_MANDATORY", "mandatory", "Mandatory (*)", "Mandatory (*)", pass),
                new UiValidationFailure("OWNER_SITE_UI_MANDATORY", "color", "a", "a", pass),
                new UiValidationFailure("OWNER_SITE", "field state", "EDITABLE", "EDITABLE", pass),
                // a field that is itself called ..._LABEL, with no base field present, must NOT be folded
                new UiValidationFailure("PAYMENT_PANEL_CHEQUE_LABEL", "value", "x", "x", pass));
        var groups = com.gsl.validation.ui.UiValidationReporter.groupByComponent(rs);
        assertEquals(groups.keySet().stream().toList(), List.of("OWNER_SITE", "PAYMENT_PANEL_CHEQUE_LABEL"));
        assertEquals(groups.get("OWNER_SITE").size(), 5);
        assertEquals(com.gsl.validation.ui.UiValidationReporter.propertyLabel(rs.get(1)), "label font-size");
        assertEquals(com.gsl.validation.ui.UiValidationReporter.propertyLabel(rs.get(2)), "mandatory");
        assertEquals(com.gsl.validation.ui.UiValidationReporter.propertyLabel(rs.get(3)), "required marker color");
        assertEquals(com.gsl.validation.ui.UiValidationReporter.propertyLabel(rs.get(0)), "label text");
    }

    @Test
    public void groupHeadingUsesTheWorkbookDisplayNameElseHumanises() {
        UiStyleScope.set(VoucherDomain.GV);
        assertEquals(com.gsl.validation.ui.UiValidationReporter.displayName("CASH_BANK"), "Cash/Bank");
        assertEquals(com.gsl.validation.ui.UiValidationReporter.displayName("SOME_UNLISTED_FIELD"), "Some Unlisted Field");
    }

    @Test
    public void rowTitleTagsBecomeInlineChips() {
        String html = com.gsl.validation.ui.UiValidationReporter.titleWithChips(
                "Row 2 · GL: Interest on Loan CC. A/c. · Dr 2.00 · Sub Ledger: no · Site: yes · Payment: no");
        assertTrue(html.contains("Row 2 · GL: Interest on Loan CC. A/c. · Dr 2.00"));
        assertTrue(html.contains("Site: yes") && html.contains("Payment: no") && html.contains("Sub Ledger: no"));
        assertTrue(!html.contains("· Site: yes"), html);
    }

    @Test
    public void nodeOrderPutsPaymentAfterGridAndGridFooterLast() {
        java.util.function.Function<String, String> id = s -> s;
        assertEquals(com.gsl.validation.ui.UiValidationReporter.orderNodes(
                List.of("Payment Information", "Expense/Income Detail", "Page Footer", "Document Information"), id, true),
                List.of("Document Information", "Expense/Income Detail", "Payment Information", "Page Footer"));
        assertEquals(com.gsl.validation.ui.UiValidationReporter.orderNodes(
                List.of("Grid header and toolbar", "Grid footer", "Row 1", "Row 2"), id, false),
                List.of("Grid header and toolbar", "Row 1", "Row 2", "Grid footer"));
    }

    @Test
    public void heldResultsAreParkedOnceAndClearedOnFlush() {
        com.gsl.validation.ui.UiFailureCollector.clear();
        com.gsl.validation.ui.UiValidationReporter.clearHeld();
        UiValidationFailure f = new UiValidationFailure("X", "p", "a", "b", UiValidationFailure.Status.FAIL);
        com.gsl.validation.ui.UiValidationReporter.holdForFill(List.of(f));
        assertEquals(com.gsl.validation.ui.UiFailureCollector.pending().size(), 1);
        com.gsl.validation.ui.UiValidationReporter.clearHeld();
        com.gsl.validation.ui.UiFailureCollector.clear();
    }

    @Test
    public void elementTypeCellMayListAlternatives() {
        assertEquals(UiElementType.parseAll("Text field|Dropdown"),
                List.of(UiElementType.TEXT_FIELD, UiElementType.DROPDOWN));
        assertEquals(UiElementType.parseAll(null), List.of());
        assertEquals(UiElementType.parseAll("-"), List.of());
        expectThrows(IllegalArgumentException.class, () -> UiElementType.parseAll("Text field|Gizmo"));
    }
}
