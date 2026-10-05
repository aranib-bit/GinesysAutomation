package com.tests.Finance;

import com.gsl.api.bulk.K6ProcessBulkDataProvisioner;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

/**
 * Parsing of k6's own console wrapping around fillDocScheme.js output. The sample lines are real output
 * captured from a k6 2.1.0 run (non-TTY logfmt form) and the TTY form. Run with
 * {@code -Dtest=DocSchemeFillerParsingTest}.
 */
public class DocSchemeFillerParsingTest {

    private static final String LOGFMT_OK =
            "time=\"2026-10-05T15:59:43+05:30\" level=info msg=\"FillVoucher [VU 1 iter 0] OK type=AR docNo=TAR/001/26-27 status=200 ms=57\" source=console";
    private static final String LOGFMT_FAIL =
            "time=\"2026-10-05T15:59:43+05:30\" level=info msg=\"FillVoucher [VU 2 iter 1] FAIL type=AR status=400 ms=9 msg=HTTP 400 - {\\\"success\\\": false, \\\"error\\\": \\\"Document number cannot be generated: scheme exhausted\\\"}\" source=console";

    @Test
    public void unwrapsLogfmtLines() {
        assertEquals(K6ProcessBulkDataProvisioner.unwrapConsoleLine(LOGFMT_OK),
                "FillVoucher [VU 1 iter 0] OK type=AR docNo=TAR/001/26-27 status=200 ms=57");
    }

    @Test
    public void unwrapsLogfmtLinesWithEscapedQuotesInTheBody() {
        String plain = K6ProcessBulkDataProvisioner.unwrapConsoleLine(LOGFMT_FAIL);
        assertTrue(plain.startsWith("FillVoucher [VU 2 iter 1] FAIL type=AR status=400"), plain);
        assertTrue(plain.contains("{\"success\": false, \"error\": \"Document number cannot be generated: scheme exhausted\"}"), plain);
    }

    @Test
    public void unwrapsTtyLines() {
        assertEquals(K6ProcessBulkDataProvisioner.unwrapConsoleLine(
                        "INFO[0001] FillVoucher [VU 3 iter 12] OK type=GV docNo=VG/012/26-27 status=201 ms=345  source=console"),
                "FillVoucher [VU 3 iter 12] OK type=GV docNo=VG/012/26-27 status=201 ms=345");
    }

    @Test
    public void leavesOtherLinesUntouched() {
        String summary = "K6_SUMMARY created=3 failed=2";
        assertEquals(K6ProcessBulkDataProvisioner.unwrapConsoleLine(summary), summary);
        String other = "running (00m01.0s), 2/2 VUs, 5 complete and 0 interrupted iterations";
        assertEquals(K6ProcessBulkDataProvisioner.unwrapConsoleLine(other), other);
    }

    @Test
    public void parsesFillAndSummaryLinesAfterUnwrapping() throws Exception {
        Class<?> filler = Class.forName("com.gsl.api.bulk.DocSchemeFiller");
        var parseFill = filler.getDeclaredMethod("parseFillLine", String.class);
        var parseSummary = filler.getDeclaredMethod("parseSummary", String.class);
        parseFill.setAccessible(true);
        parseSummary.setAccessible(true);

        Object ok = parseFill.invoke(null, K6ProcessBulkDataProvisioner.unwrapConsoleLine(LOGFMT_OK));
        assertNotNull(ok);
        assertTrue(ok.toString().contains("vu=1") && ok.toString().contains("iter=0") && ok.toString().contains("ok=true"), ok.toString());

        Object fail = parseFill.invoke(null, K6ProcessBulkDataProvisioner.unwrapConsoleLine(LOGFMT_FAIL));
        assertNotNull(fail);
        assertTrue(fail.toString().contains("vu=2") && fail.toString().contains("ok=false"), fail.toString());

        assertEquals(parseFill.invoke(null, "some other k6 line"), null);
        int[] totals = (int[]) parseSummary.invoke(null, "K6_SUMMARY created=3 failed=2");
        assertEquals(totals[0], 3);
        assertEquals(totals[1], 2);
        assertEquals(parseSummary.invoke(null, "FillVoucher [VU 1 iter 0] OK"), null);
    }
}
