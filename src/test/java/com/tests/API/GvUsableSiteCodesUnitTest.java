package com.tests.API;

import com.gsl.model.adm.AdmSite;
import com.gsl.utils.dataprovider.source.finance.DbGeneralVoucherDataSource;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.testng.Assert.assertEquals;

/** Browser/DB-free checks of {@link DbGeneralVoucherDataSource#usableSiteCodes}: the rule that decides which sites a
 *  GV row (and a backfilled Sub Ledger / GL) may use. Run with {@code -Denv=clone-staging -Dtest=GvUsableSiteCodesUnitTest}. */
public class GvUsableSiteCodesUnitTest {

    private static AdmSite site(long code, long ou, String ext) {
        AdmSite s = new AdmSite();
        s.setCode(code);
        s.setAdmouCode(ou);
        s.setExt(ext);
        return s;
    }

    private static final Map<Long, AdmSite> SITES = Map.of(
            1L, site(1, 1, "N"),
            2L, site(2, 1, "N"),
            3L, site(3, 1, "Y"),   // extinct
            43L, site(43, 3, "N"), // other OU
            5L, site(5, 1, null)); // ext not read

    private static List<Long> usable(List<Long> candidates, Set<Long> assigned, Long ou, int limit) {
        return DbGeneralVoucherDataSource.usableSiteCodes(candidates, assigned, ou, SITES::get, limit);
    }

    @Test
    public void dropsOtherOuExtinctAndUnassignedSites() {
        assertEquals(usable(List.of(43L, 3L, 2L, 1L, 99L), Set.of(1L, 2L, 3L, 43L), 1L, 10), List.of(2L, 1L));
    }

    @Test
    public void respectsLimitAndDistinct() {
        assertEquals(usable(List.of(1L, 1L, 2L, 5L), Set.of(1L, 2L, 5L), 1L, 2), List.of(1L, 2L));
    }

    @Test
    public void nullOwnerOuSkipsOuTestOnly() {
        assertEquals(usable(List.of(43L, 3L, 1L), Set.of(1L, 3L, 43L), null, 10), List.of(43L, 1L));
    }

    @Test
    public void gl_with_only_one_sameOu_site_cannot_serve_a_three_site_row() {
        assertEquals(usable(List.of(1L, 43L, 3L), Set.of(1L, 3L, 43L), 1L, 3).size(), 1);
    }
}
