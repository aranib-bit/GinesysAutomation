package com.tests.API;

import com.gsl.api.model.audit.AuditRow;
import com.gsl.validation.api.AuditApiReader.Capture;
import com.gsl.validation.api.GeneralVoucherApiAuditChecks;
import com.gsl.validation.api.GeneralVoucherApiStateChecks.Result;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Audit checks on payloads shaped like the recorded Audit*.har responses (full-row snapshots per version, odd column casing). */
public class GeneralVoucherApiAuditChecksUnitTest {

    private static AuditRow main(int v, String action, String status, double credit, String remarks) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("versioN_SEQ", v);
        m.put("action", action);
        m.put("logiN_USER", "Arani Barman");
        m.put("transactioN_DATE", "2026-10-28T13:4" + v + ":47.6");
        m.put("transactioN_ID", "36386613" + v);
        m.put("releasE_STATUS", status);
        m.put("crediT_AMOUNT", credit);
        m.put("remarkS_OR_NARRATION", remarks);
        m.put("voucheR_NUMBER", "VG/00529/26-27");
        return new AuditRow(m);
    }

    private static AuditRow det(int v, String action, String gl, double debit, int code) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("versioN_SEQ", v);
        m.put("action", action);
        m.put("code", code);
        m.put("generaL_LEDGER_NAME", gl);
        m.put("debiT_AMOUNT", debit);
        m.put("crediT_AMOUNT", 0.0);
        return new AuditRow(m);
    }

    private static Capture capture(List<AuditRow> main, List<AuditRow> det, List<AuditRow> site) {
        int max = 0;
        for (AuditRow r : main) max = Math.max(max, r.version());
        for (AuditRow r : det) max = Math.max(max, r.version());
        int siteMax = site.stream().mapToInt(AuditRow::version).max().orElse(0);
        return new Capture("VG/00529/26-27", "1183795", true, true, main, det, site, max, siteMax);
    }

    @Test
    public void volatileColumnsAreNotDataChanges() {
        AuditRow a = main(1, "INSERT", "Posted", 222, "x");
        AuditRow b = main(2, "UPDATE", "Posted", 222, "x");
        Assert.assertTrue(b.changedFrom(a).isEmpty(), "only version/user/timestamp/transaction id differ");
        AuditRow c = main(3, "UPDATE", "Unposted", 200, "x");
        Assert.assertEquals(c.changedFrom(b).keySet().stream().sorted().toList(), List.of("crediT_AMOUNT", "releasE_STATUS"));
    }

    @Test
    public void revertPassesWhenOnlyReleaseStatusChanged() {
        Capture before = capture(List.of(main(1, "INSERT", "Posted", 222, "x")), List.of(det(1, "INSERT", "GL", 222, 7)), List.of());
        Capture after = capture(List.of(main(1, "INSERT", "Posted", 222, "x"), main(2, "UPDATE", "Unposted", 222, "x")),
                List.of(det(1, "INSERT", "GL", 222, 7)), List.of());
        Result r = GeneralVoucherApiAuditChecks.reverted(before, after, false, "Unposted");
        Assert.assertTrue(r.ok(), String.join("; ", r.failed()));
    }

    @Test
    public void revertFailsWhenAnotherColumnChanged() {
        Capture before = capture(List.of(main(1, "INSERT", "Posted", 222, "x")), List.of(), List.of());
        Capture after = capture(List.of(main(1, "INSERT", "Posted", 222, "x"), main(2, "UPDATE", "Unposted", 999, "x")), List.of(), List.of());
        Result r = GeneralVoucherApiAuditChecks.reverted(before, after, false, "Unposted");
        Assert.assertFalse(r.ok());
        Assert.assertTrue(r.failed().stream().anyMatch(f -> f.contains("crediT_AMOUNT")), r.failed().toString());
    }

    @Test
    public void blockedRevertExpectsNoNewVersion() {
        Capture before = capture(List.of(main(1, "INSERT", "Posted", 222, "x")), List.of(), List.of());
        Assert.assertTrue(GeneralVoucherApiAuditChecks.reverted(before, before, true, null).ok());
        Capture after = capture(List.of(main(1, "INSERT", "Posted", 222, "x"), main(2, "UPDATE", "Unposted", 222, "x")), List.of(), List.of());
        Assert.assertFalse(GeneralVoucherApiAuditChecks.reverted(before, after, true, null).ok());
    }

    @Test
    public void deletePassesWhenDeleteRowsMirrorTheLastVersion() {
        Capture before = capture(List.of(main(1, "INSERT", "Posted", 222, "x")), List.of(det(1, "INSERT", "GL", 222, 7)), List.of());
        Capture after = capture(List.of(main(1, "INSERT", "Posted", 222, "x"), main(2, "DELETE", "Posted", 222, "x")),
                List.of(det(1, "INSERT", "GL", 222, 7), det(2, "DELETE", "GL", 222, 7)), List.of());
        Result r = GeneralVoucherApiAuditChecks.deleted(before, after);
        Assert.assertTrue(r.ok(), String.join("; ", r.failed()));
    }

    @Test
    public void deleteFailsWithoutADeleteRow() {
        Capture before = capture(List.of(main(1, "INSERT", "Posted", 222, "x")), List.of(det(1, "INSERT", "GL", 222, 7)), List.of());
        Result r = GeneralVoucherApiAuditChecks.deleted(before, before);
        Assert.assertFalse(r.ok());
    }

    @Test
    public void rowsAreGroupedUnderAuditGroups() {
        Capture before = capture(List.of(main(1, "INSERT", "Posted", 222, "x")), List.of(), List.of());
        Capture after = capture(List.of(main(1, "INSERT", "Posted", 222, "x"), main(2, "UPDATE", "Unposted", 222, "x")), List.of(), List.of());
        Result r = GeneralVoucherApiAuditChecks.reverted(before, after, false, "Unposted");
        Assert.assertTrue(r.rows().stream().allMatch(x -> x.group().startsWith("Audit")), r.rows().toString());
        Assert.assertTrue(r.rows().stream().anyMatch(x -> x.group().equals("Audit · State")));
    }
}
