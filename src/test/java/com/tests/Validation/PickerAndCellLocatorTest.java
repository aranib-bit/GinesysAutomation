package com.tests.Validation;

import com.gsl.testdata.dto.MergedFinanceApVoucherData;
import com.gsl.utils.Selenium.GridCellLocator;
import com.gsl.validation.ui.PickerUi;
import com.gsl.validation.ui.UiValidationFailure;
import org.testng.annotations.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/** Browser-free checks for the Round-17 pieces: cell locator, saved-amount rule, picker parsing and rules. */
public class PickerAndCellLocatorTest {

    @Test
    public void cellLocatorUsesColumnIdOrItemIdNeverPosition() {
        String x = GridCellLocator.xpath("//div[@id='w']", 0, "Ledger Narration", "ledgerNarrationCode");
        assertTrue(x.contains("@data-columnid=//div[@id='w']//span[normalize-space()='Ledger Narration']"), x);
        assertTrue(x.contains("' x-column-header '"), x);
        assertTrue(x.contains("@data-columnid='ledgerNarrationCode'"), x);
        assertFalse(x.contains("count("), x);
        assertTrue(x.endsWith("]/div"), x);
        assertFalse(GridCellLocator.xpath("", 1, "Amount (Dr)", null).contains(" or "));
    }

    @Test
    public void savedAmountFollowsTheMismatchPlanOnlyForASingleRow() {
        BigDecimal line = new BigDecimal("16.00");
        BigDecimal plan = new BigDecimal("8.00");
        assertEquals(MergedFinanceApVoucherData.chooseSavedAmount(line, plan, true, 1), plan);
        assertEquals(MergedFinanceApVoucherData.chooseSavedAmount(line, plan, false, 1), line);
        assertEquals(MergedFinanceApVoucherData.chooseSavedAmount(line, null, true, 1), line);
        assertEquals(MergedFinanceApVoucherData.chooseSavedAmount(line, plan, true, 2), line);
    }

    private static final String BEFORE = "{\"shown\":true,\"headers\":[\"Name\",\"Id\",\"Class Name\"],"
            + "\"rows\":[[\"(sp)Chandu warehouse rent\",\"0021\",\"Others\"],[\"AGENT1\",\"AGENT1\",\"Transporter\"]],"
            + "\"mism\":0,\"page\":1,\"of\":11,\"b\":{\"first\":false,\"prev\":false,\"next\":true,\"last\":true,\"refresh\":true}}";
    private static final String AFTER = "{\"shown\":true,\"headers\":[\"Name\",\"Id\",\"Class Name\"],"
            + "\"rows\":[[\"AGENT1\",\"AGENT1\",\"Transporter\"]],"
            + "\"mism\":0,\"page\":1,\"of\":1,\"b\":{\"first\":false,\"prev\":false,\"next\":false,\"last\":false,\"refresh\":true}}";

    private static boolean noFail(List<UiValidationFailure> r) {
        return r.stream().noneMatch(UiValidationFailure::isFail);
    }

    @Test
    public void pickerSnapshotParsesAndPassesTheRules() {
        PickerUi.Snapshot b = PickerUi.parse(BEFORE);
        PickerUi.Snapshot a = PickerUi.parse(AFTER);
        assertTrue(b.shown());
        assertEquals(b.totalPages(), Integer.valueOf(11));
        assertEquals(a.rows().size(), 1);
        PickerUi.Observation o = new PickerUi.Observation("Sub Ledger picker", List.of("Name", "Id", "Class Name"),
                "AGENT", "AGENT1", "AGENT1", Map.of("Class Name", "Transporter"), b, a);
        assertTrue(noFail(PickerUi.state("P", o, b)));
        assertTrue(noFail(PickerUi.state("P", o, a)));
        assertTrue(noFail(PickerUi.filtered("P", o)));
        assertTrue(noFail(PickerUi.change("P", b, a)));
        assertFalse(PickerUi.parse("{\"shown\":false}").shown());
    }

    @Test
    public void pickerRulesCatchWrongPagingAndMissingTarget() {
        PickerUi.Snapshot a = PickerUi.parse(AFTER.replace("\"next\":false", "\"next\":true"));
        PickerUi.Observation o = new PickerUi.Observation("Sub Ledger picker", List.of("Name", "Id", "Class Name"),
                "AGENT", "NOT THERE", null, Map.of(), null, a);
        assertFalse(noFail(PickerUi.footer("P", a)));          // Next enabled on page 1 of 1
        assertFalse(noFail(PickerUi.filtered("P", o)));        // target absent, no further pages
        PickerUi.Observation wrongCols = new PickerUi.Observation("x", List.of("Name", "Id", "Ledger", "Class Name"),
                "AGENT", "AGENT1", null, Map.of(), null, a);
        assertFalse(noFail(PickerUi.state("P", wrongCols, a)));
    }

    @Test
    public void unloadedListBeforeTypingIsASkipNotAFailure() {
        PickerUi.Snapshot unloaded = PickerUi.parse("{\"shown\":true,\"headers\":[\"Name\",\"Id\",\"Class Name\"],"
                + "\"rows\":[],\"mism\":0,\"page\":0,\"of\":0,\"b\":{}}");
        assertFalse(PickerUi.loaded(unloaded));
        PickerUi.Observation o = new PickerUi.Observation("Sub Ledger picker", List.of("Name", "Id", "Class Name"),
                "AGENT", "AGENT1", null, Map.of(), unloaded, PickerUi.parse(AFTER));
        List<UiValidationFailure> r = PickerUi.validate(o);
        assertTrue(noFail(r), r.toString());
        assertTrue(r.stream().anyMatch(UiValidationFailure::isSkip));
    }

    @Test
    public void targetOnALaterPagePassesThePagingRules() {
        PickerUi.Snapshot page2 = PickerUi.parse("{\"shown\":true,\"headers\":[\"Name\"],\"rows\":[[\"AGENT1\"]],"
                + "\"mism\":0,\"page\":2,\"of\":3,\"b\":{\"first\":true,\"prev\":true,\"next\":true,\"last\":true,\"refresh\":true}}");
        assertTrue(noFail(PickerUi.footer("P", page2)));
    }
}
