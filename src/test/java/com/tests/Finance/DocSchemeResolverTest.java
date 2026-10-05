package com.tests.Finance;

import com.gsl.model.adm.AdmDocSchemeCandidate;
import com.gsl.utils.dataprovider.source.finance.common.DocSchemeMode;
import com.gsl.utils.dataprovider.source.finance.common.DocSchemeResolver;
import com.gsl.utils.dataprovider.source.finance.common.DocSchemeSelection;
import org.testng.SkipException;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Set;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

/**
 * Ranking rules of {@link DocSchemeResolver#select} — pure, no DB/browser. Numbers mirror the real
 * clone-idealdb data (ycode 76): 138 "AR/AP Voucher" is full, 212 "AR VOUCHER" has plenty left, and
 * 215 "AR AP Test" is a small-width dedicated test scheme.
 * Run with {@code -Dtest=DocSchemeResolverTest}.
 */
public class DocSchemeResolverTest {

    private static final Set<String> TEST_NAMES = Set.of("ar ap test", "gv test");
    private static final String MENU = "FinanceARVoucher";

    private static AdmDocSchemeCandidate scheme(long code, String name, long width, String interval,
                                                String manual, Long lastnoYear) {
        return AdmDocSchemeCandidate.builder()
                .doccode(code).docname(name).width(width).starting(1L)
                .manual(manual).generationInterval(interval).ext("N")
                .lastnoYear(lastnoYear).lastnoAny(lastnoYear)
                .build();
    }

    private static DocSchemeSelection pick(List<AdmDocSchemeCandidate> rows, DocSchemeMode mode, int headroom) {
        return DocSchemeResolver.select(rows, MENU, 76, 1, mode, headroom, TEST_NAMES);
    }

    private static List<AdmDocSchemeCandidate> cloneIdealDbAr() {
        return List.of(
                scheme(138, "AR/AP Voucher", 5, "Y", "N", 99999L),
                scheme(212, "AR VOUCHER", 5, "Y", "N", 36L),
                scheme(215, "AR AP Test", 3, "Y", "N", 5L));
    }

    @Test
    public void autoSkipsExhaustedAndTestSchemesAndPicksMostRemaining() {
        DocSchemeSelection s = pick(cloneIdealDbAr(), DocSchemeMode.AUTO, 1);
        assertEquals(s.docName(), "AR VOUCHER");
        assertEquals(s.getChosen().getRemaining(), Long.valueOf(99963));
        // every candidate is still reported for the log, with derived fields
        assertEquals(s.getCandidates().size(), 3);
        assertTrue(s.getNarrative().stream().anyMatch(l -> l.contains("'AR/AP Voucher'") && l.contains("remaining=0")));
    }

    @Test
    public void autoFailsFastListingCandidatesWhenEverySchemeIsFull() {
        List<AdmDocSchemeCandidate> rows = List.of(scheme(138, "AR/AP Voucher", 5, "Y", "N", 99999L));
        IllegalStateException e = expectThrows(IllegalStateException.class, () -> pick(rows, DocSchemeMode.AUTO, 1));
        assertTrue(e.getMessage().contains("AR/AP Voucher") && e.getMessage().contains("remaining=0"), e.getMessage());
    }

    @Test
    public void minHeadroomTreatsAlmostFullSchemeAsExhausted() {
        AdmDocSchemeCandidate nearlyFull = scheme(1, "Nearly Full", 3, "Y", "N", 997L);   // remaining 2
        assertEquals(pick(List.of(nearlyFull), DocSchemeMode.AUTO, 2).docName(), "Nearly Full");
        expectThrows(IllegalStateException.class, () -> pick(List.of(nearlyFull), DocSchemeMode.AUTO, 3));
        assertEquals(pick(List.of(nearlyFull), DocSchemeMode.EXHAUSTED, 3).docName(), "Nearly Full");
    }

    @Test
    public void missingCounterRowMeansFreshCounter() {
        DocSchemeSelection s = pick(List.of(scheme(7, "Fresh", 5, "Y", "N", null)), DocSchemeMode.AUTO, 1);
        assertEquals(s.getChosen().getRemaining(), Long.valueOf(99999));
        assertEquals(s.getChosen().getLastUsed(), Long.valueOf(0));
    }

    @Test
    public void untrackedIntervalIsAvailableButRankedAfterTrackedSchemes() {
        List<AdmDocSchemeCandidate> rows = List.of(
                scheme(1, "Monthly", 5, "M", "N", null),
                scheme(2, "Yearly", 5, "Y", "N", 10L));
        assertEquals(pick(rows, DocSchemeMode.AUTO, 1).docName(), "Yearly");
        assertEquals(pick(List.of(rows.get(0)), DocSchemeMode.AUTO, 1).docName(), "Monthly");
    }

    @Test
    public void extinctSchemesAreNeverChosen() {
        AdmDocSchemeCandidate extinct = scheme(1, "Dead", 5, "Y", "N", 1L);
        extinct.setExt("Y");
        expectThrows(IllegalStateException.class, () -> pick(List.of(extinct), DocSchemeMode.AUTO, 1));
    }

    @Test
    public void exhaustedModePrefersFullTestSchemeOverFullRealScheme() {
        List<AdmDocSchemeCandidate> rows = List.of(
                scheme(138, "AR/AP Voucher", 5, "Y", "N", 99999L),
                scheme(215, "AR AP Test", 3, "Y", "N", 999L));
        assertEquals(pick(rows, DocSchemeMode.EXHAUSTED, 1).docName(), "AR AP Test");
    }

    @Test
    public void exhaustedModeFallsBackToFullRealSchemeAndSkipsWhenNoneIsFull() {
        assertEquals(pick(cloneIdealDbAr(), DocSchemeMode.EXHAUSTED, 1).docName(), "AR/AP Voucher");
        List<AdmDocSchemeCandidate> noneFull = List.of(scheme(212, "AR VOUCHER", 5, "Y", "N", 36L));
        expectThrows(SkipException.class, () -> pick(noneFull, DocSchemeMode.EXHAUSTED, 1));
    }

    @Test
    public void manualModePicksManualSchemeIgnoringCapacity() {
        List<AdmDocSchemeCandidate> rows = List.of(
                scheme(212, "AR VOUCHER", 5, "Y", "N", 36L),
                scheme(300, "Manual Full", 3, "Y", "Y", 999L));
        assertEquals(pick(rows, DocSchemeMode.MANUAL, 1).docName(), "Manual Full");
        expectThrows(IllegalStateException.class, () -> pick(List.of(rows.get(0)), DocSchemeMode.MANUAL, 1));
    }

    @Test
    public void autoNeverPicksManualSchemes() {
        List<AdmDocSchemeCandidate> rows = List.of(scheme(300, "Manual Only", 5, "Y", "Y", 1L));
        expectThrows(IllegalStateException.class, () -> pick(rows, DocSchemeMode.AUTO, 1));
    }
}
