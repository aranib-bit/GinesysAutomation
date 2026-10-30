package com.tests.API;

import com.gsl.api.bulk.PerformanceRuns;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Map;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;

/** The environment the read-only performance scenarios take: nothing is invented, a budget is set only when given. */
public class PerformanceRunsUnitTest {

    @Test
    public void auditReadsEnvCarriesTheScopeAndOnlyTheBudgetsGiven() {
        Map<String, String> env = PerformanceRuns.auditReadsEnv("qaprod", "FinVoucher", 14, List.of("aud2_finvchmain", "aud2_finvchdet"), 2, 4, "2m", 1500, null);
        assertEquals(env.get("AUDIT_TABLES"), "aud2_finvchmain,aud2_finvchdet");
        assertEquals(env.get("AUDIT_DAYS"), "14");
        assertEquals(env.get("AUDIT_LIST_P95_MS"), "1500");
        assertFalse(env.containsKey("AUDIT_HISTORY_P95_MS"));
        assertEquals(env.get("TARGET_VUS"), "4");
        assertEquals(env.get("DURATION"), "2m");
        assertEquals(env.get("TENANT"), "qaprod");
    }

    @Test
    public void noTablesMeansTheDocumentListOnly() {
        assertEquals(PerformanceRuns.auditReadsEnv("qaprod", "FinVoucher", 30, List.of(), 3, 0, null, null, null).get("AUDIT_TABLES"), "");
    }

    @Test
    public void listPartiesEnvScopesTheListToTheSiteAndOptionallyARunPrefix() {
        Map<String, String> env = PerformanceRuns.listPartiesEnv("qaprod", "VENDOR", 1, 2, "ZB2026", 3, "1m", null);
        assertEquals(env.get("PARTY_FAMILY"), "VENDOR");
        assertEquals(env.get("CONNECTED_SITE"), "1");
        assertEquals(env.get("CONNECTED_OU"), "2");
        assertEquals(env.get("SLID_PREFIX"), "ZB2026");
        assertFalse(env.containsKey("LIST_PAGE_P95_MS"));
        assertFalse(PerformanceRuns.listPartiesEnv("qaprod", "CUSTOMER", 1, 2, null, 3, "1m", null).containsKey("SLID_PREFIX"));
    }
}