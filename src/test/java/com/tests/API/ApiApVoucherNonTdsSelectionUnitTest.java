package com.tests.API;

import com.gsl.api.model.common.SubLedgerLookupResult;
import com.gsl.utils.dataprovider.source.finance.ApiApVoucherDataSource;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Set;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

/** The advance-payment WITHOUT TDS must never be given a TDS-applicable party (FIN-AP-ADD-ADV-01: the grid then demands a TDS nature and rejects the save). */
public class ApiApVoucherNonTdsSelectionUnitTest {

    private static SubLedgerLookupResult sl(long code, String classType, String tdsApp) {
        return SubLedgerLookupResult.builder().slCode(code).slName("SL" + code).classType(classType).tdsApp(tdsApp).build();
    }

    @Test
    public void aSubLedgerTheLookupFlagsTdsApplicableIsNeverOffered() {
        List<SubLedgerLookupResult> out = ApiApVoucherDataSource.nonTdsAdvanceCandidates(
                List.of(sl(1, "S", "YES"), sl(2, "S", "NO")), Set.of(), 1);
        assertEquals(out.size(), 1);
        assertEquals(out.get(0).getSlCode(), 2L);
    }

    @Test
    public void aVendorTheVendorMasterFlagsTdsApplicableIsNeverOfferedEvenIfTheLookupSaysNo() {
        List<SubLedgerLookupResult> out = ApiApVoucherDataSource.nonTdsAdvanceCandidates(
                List.of(sl(1, "S", "NO"), sl(2, "S", "NO")), Set.of(1L), 1);
        assertEquals(out.get(0).getSlCode(), 2L);
    }

    @Test
    public void suppliersComeFirstAndOnlyTheNeededCountIsReturned() {
        List<SubLedgerLookupResult> out = ApiApVoucherDataSource.nonTdsAdvanceCandidates(
                List.of(sl(10, "C", null), sl(11, "S", null), sl(12, "S", "NO")), Set.of(), 2);
        assertEquals(out.size(), 2);
        assertTrue(out.stream().allMatch(s -> "S".equals(s.getClassType())), out.toString());
    }

    @Test
    public void whenEveryCandidateIsTdsApplicableNothingIsReturnedSoTheScenarioSkipsInsteadOfFailing() {
        assertTrue(ApiApVoucherDataSource.nonTdsAdvanceCandidates(List.of(sl(1, "S", "YES"), sl(2, "S", "NO")), Set.of(2L), 1).isEmpty());
    }
}