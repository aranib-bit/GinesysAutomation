package com.gsl.validation.api;

import com.gsl.api.model.audit.AuditRow;
import com.gsl.testdata.Finance.Operations.Vouchers.GeneralVoucher.DetailLineEditTarget;
import com.gsl.testdata.Finance.Operations.Vouchers.GeneralVoucher.DocumentInformation;
import com.gsl.testdata.Finance.Operations.Vouchers.GeneralVoucher.ExpenseIncomeDetail;
import com.gsl.testdata.Finance.Operations.Vouchers.GeneralVoucher.PaymentInformation;
import com.gsl.testdata.Finance.Operations.Vouchers.GeneralVoucher.SiteAllocation;
import com.gsl.testdata.dto.MergedFinanceGeneralVoucherData;
import com.gsl.utils.dataprovider.fixture.finance.GeneralVoucherEditVariant;
import com.gsl.validation.api.AuditApiReader.Capture;
import com.gsl.validation.api.GeneralVoucherApiStateChecks.Result;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * {@code db.available=no} audit validation for the General Voucher flows, from what the Audit API holds (full-row snapshots per version of
 * FINVCHMAIN / FINVCHDET and FINCOSTTAG) instead of the {@code aud2_*} tables. Pure functions over {@link Capture}s so they can be unit
 * tested on recorded payloads; groups are named {@code Audit · ...} so they sit in the same API Validation tree as the rootview checks.
 *
 * <p>Version numbers of FINVCHMAIN / FINVCHDET are one sequence per voucher, so "what an action wrote" is every row whose
 * {@code versioN_SEQ} is above the highest one captured before it. FINCOSTTAG has its own sequence (an amount change is a DELETE of the old
 * row plus an INSERT of the new one).
 */
public final class GeneralVoucherApiAuditChecks {

    static final String G_STATE = "Audit · State";
    static final String G_MAIN = "Audit · Voucher Header (FINVCHMAIN)";
    static final String G_DET = "Audit · Voucher Detail (FINVCHDET)";
    static final String G_SITE = "Audit · Site Allocation (FINCOSTTAG)";

    /** Header columns an edit may change on top of the edited ones: who last modified it, the user-defined numeric field the app initialises,
     *  and the release trio — the edit flow answers Yes to "Document will be released after save" (DocumentPopup#handleReleaseConfirmationIfPresent),
     *  so Unposted → Posted is expected. The DB-mode VoucherEditChangeSets already ignores the same columns. */
    private static final Set<String> HEADER_EDIT_NOISE = ci("lastmodifiedbY_USERCODE", "lastmodifiedbY_USER", "udfnuM01",
            "releaseD_USERCODE", "releaseD_USER", "releasE_STATUS");

    private GeneralVoucherApiAuditChecks() {}

    // ───────────────────────────── Create ─────────────────────────────

    public static Result created(MergedFinanceGeneralVoucherData data, Capture after) {
        ApiChecks c = new ApiChecks();
        state(c, after);

        // Header: exactly one INSERT, never a DELETE, carrying what was entered.
        List<AuditRow> main = after.main();
        c.eq(G_MAIN + " | INSERT versions", 1L, main.stream().filter(r -> "INSERT".equalsIgnoreCase(r.action())).count());
        c.eq(G_MAIN + " | DELETE versions", 0L, main.stream().filter(r -> "DELETE".equalsIgnoreCase(r.action())).count());
        AuditRow head = main.isEmpty() ? null : main.get(0);
        DocumentInformation doc = data.getDocumentInformation();
        if (head != null && doc != null) {
            c.eq(G_MAIN + " | Voucher number", after.voucherNo(), head.str("voucheR_NUMBER"));
            c.eq(G_MAIN + " | Document date", doc.getDocumentdate(), date(head.str("voucheR_DATE")));
            c.eq(G_MAIN + " | Remarks", norm(doc.getRemarks()), norm(head.str("remarkS_OR_NARRATION")));
            c.eq(G_MAIN + " | Reference", norm(doc.getReference()), norm(head.str("referencE_NUMBER")));
        }

        // Detail: one INSERT per entered line, matched by ledger + amount + side.
        List<ExpenseIncomeDetail> lines = data.getExpenseIncomeDetailList() == null ? List.of() : data.getExpenseIncomeDetailList();
        List<AuditRow> inserts = after.det().stream().filter(r -> "INSERT".equalsIgnoreCase(r.action())).collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        c.eq(G_DET + " | INSERT versions (lines)", (long) lines.size(), (long) inserts.size());
        for (int i = 0; i < lines.size(); i++) {
            ExpenseIncomeDetail e = lines.get(i);
            String group = "Audit · Row " + (i + 1);
            c.title(group, "Audit · " + ApiLineTitles.fromExpected(i, e, data.getSiteAllocationsForLine(e.getLineNo()), paymentFor(data, e, i)));
            BigDecimal amount = amountOf(e);
            AuditRow hit = take(inserts, e.getGeneralLedger(), amount, e.isDebit());
            c.eq(group + " | Audit row found", true, hit != null);
            if (hit == null) continue;
            c.eq(group + " | GL", trim(e.getGeneralLedger()), trim(hit.str("generaL_LEDGER_NAME")));
            c.money(group + " | Amount", amount, e.isDebit() ? hit.num("debiT_AMOUNT") : hit.num("crediT_AMOUNT"));
            if (norm(e.getSubLedger()) != null) c.eq(group + " | Sub-ledger", trim(e.getSubLedger()), trim(hit.str("subledgeR_NAME")));
            if (norm(e.getLedgerNarration()) != null) c.eq(group + " | Narration", norm(e.getLedgerNarration()), norm(hit.str("narration")));
            PaymentInformation pay = paymentFor(data, e, i);
            if (pay != null && pay.getPaymentMode() != null) {
                c.eq(group + " | Payment mode", pay.getPaymentMode().dbCode(), hit.str("paymenT_MODE"));
                if (norm(pay.getInstrumentNo()) != null) c.eq(group + " | Instrument number", norm(pay.getInstrumentNo()), norm(hit.str("instrumenT_NUMBER")));
            }
        }

        // Site allocation: every entered allocation appears as an INSERT row.
        List<AuditRow> siteInserts = after.site().stream().filter(r -> "INSERT".equalsIgnoreCase(r.action())).toList();
        for (ExpenseIncomeDetail e : lines) {
            for (SiteAllocation s : data.getSiteAllocationsForLine(e.getLineNo())) {
                c.eq(G_SITE + " | " + trim(e.getGeneralLedger()) + " → " + trim(s.getSiteName()) + " " + plain(s.getAmount()),
                        true, containsSite(siteInserts, e.getGeneralLedger(), s.getSiteName(), s.getAmount()));
            }
        }
        return result(c);
    }

    // ───────────────────────────── Revert ─────────────────────────────

    /**
     * A Revert that went through writes exactly one FINVCHMAIN UPDATE changing only the release status (to {@code expectedStatusText},
     * "Unposted"); a blocked Revert ({@code blocked}) writes nothing. Detail and site-allocation rows are never touched either way.
     */
    public static Result reverted(Capture before, Capture after, boolean blocked, String expectedStatusText) {
        ApiChecks c = new ApiChecks();
        state(c, after);
        List<AuditRow> newMain = after.mainAfter(before.maxVersion());
        if (blocked) {
            c.eq(G_MAIN + " | New versions (Revert blocked)", 0L, (long) newMain.size());
        } else {
            c.eq(G_MAIN + " | New versions", 1L, (long) newMain.size());
            if (!newMain.isEmpty()) {
                AuditRow last = newMain.get(newMain.size() - 1);
                c.eq(G_MAIN + " | Action", "UPDATE", last.action());
                c.eq(G_MAIN + " | Release status", expectedStatusText, last.str("releasE_STATUS"));
                AuditRow prior = after.mainAt(before.maxVersion());
                if (prior != null) {
                    Set<String> changed = last.changedFrom(prior).keySet();
                    Set<String> unexpected = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
                    unexpected.addAll(changed);
                    unexpected.removeAll(ci("releasE_STATUS", "releaseD_USERCODE", "releaseD_USER"));
                    c.eq(G_MAIN + " | Other columns changed", "none", unexpected.isEmpty() ? "none" : String.join(", ", unexpected));
                }
            }
        }
        c.eq(G_DET + " | New versions", 0L, (long) after.detAfter(before.maxVersion()).size());
        c.eq(G_SITE + " | New versions", 0L, (long) after.siteAfter(before.siteMaxVersion()).size());
        return result(c);
    }

    // ───────────────────────────── Edit ─────────────────────────────

    public static Result edited(MergedFinanceGeneralVoucherData data, GeneralVoucherEditVariant.ScenarioType scenario,
                                Capture before, Capture after) {
        ApiChecks c = new ApiChecks();
        state(c, after);
        List<AuditRow> newMain = after.mainAfter(before.maxVersion());
        c.eq(G_MAIN + " | New versions (≥ 1)", true, !newMain.isEmpty());
        c.eq(G_MAIN + " | Actions of new versions", "UPDATE only",
                newMain.stream().allMatch(r -> "UPDATE".equalsIgnoreCase(r.action())) ? "UPDATE only"
                        : String.join(", ", newMain.stream().map(AuditRow::action).toList()));
        AuditRow prior = after.mainAt(before.maxVersion());
        AuditRow last = newMain.isEmpty() ? null : newMain.get(newMain.size() - 1);
        DocumentInformation doc = data.getDocumentInformation();
        boolean releasedByEdit = false;
        if (last != null && prior != null) {
            Set<String> allowed = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
            allowed.addAll(HEADER_EDIT_NOISE);
            if (scenario == GeneralVoucherEditVariant.ScenarioType.HEADER_ONLY) {
                allowed.addAll(ci("remarkS_OR_NARRATION", "referencE_NUMBER", "voucheR_DATE"));
            } else {
                allowed.addAll(ci("debiT_AMOUNT", "crediT_AMOUNT"));
            }
            Set<String> changed = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
            changed.addAll(last.changedFrom(prior).keySet());
            Set<String> unexpected = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
            unexpected.addAll(changed);
            unexpected.removeAll(allowed);
            c.eq(G_MAIN + " | Unexpected columns changed", "none", unexpected.isEmpty() ? "none" : String.join(", ", unexpected));
            // Unchanged, or released by the edit flow's own "release after save" Yes — never anything else.
            String before0 = prior.str("releasE_STATUS");
            String after0 = last.str("releasE_STATUS");
            boolean unchanged = java.util.Objects.equals(before0, after0);
            c.eq(G_MAIN + " | Release status after edit", unchanged ? before0 : "Posted (released on save) or unchanged",
                    unchanged ? after0 : (isPosted(after0) ? "Posted (released on save) or unchanged" : after0));
            releasedByEdit = !unchanged && isPosted(after0);
        }
        if (last != null && doc != null && scenario == GeneralVoucherEditVariant.ScenarioType.HEADER_ONLY) {
            c.eq(G_MAIN + " | Remarks", norm(doc.getRemarks()), norm(last.str("remarkS_OR_NARRATION")));
            c.eq(G_MAIN + " | Reference", norm(doc.getReference()), norm(last.str("referencE_NUMBER")));
            c.eq(G_MAIN + " | Document date", doc.getDocumentdate(), date(last.str("voucheR_DATE")));
        }

        List<AuditRow> newDet = after.detAfter(before.maxVersion());
        if (scenario == GeneralVoucherEditVariant.ScenarioType.DETAIL_LINE && data.getEditedDetailLine() != null) {
            ExpenseIncomeDetail nv = data.getEditedDetailLine().getNewValues();
            boolean add = data.getEditedDetailLine().getShape() == DetailLineEditTarget.EditShape.ADD_NEW_LINE;
            c.eq(G_DET + " | " + (add ? "INSERT" : "UPDATE") + " of " + trim(nv.getGeneralLedger()) + " " + plain(amountOf(nv)), true,
                    newDet.stream().anyMatch(r -> (add ? "INSERT" : "UPDATE").equalsIgnoreCase(r.action())
                            && trim(nv.getGeneralLedger()).equals(trim(r.str("generaL_LEDGER_NAME")))
                            && sameAmount(amountOf(nv), nv.isDebit() ? r.num("debiT_AMOUNT") : r.num("crediT_AMOUNT"))));
            if (data.getEditedDetailLine().getShape() == DetailLineEditTarget.EditShape.SITE_SPLIT_ONLY) {
                List<AuditRow> newSite = after.siteAfter(before.siteMaxVersion()).stream()
                        .filter(r -> "INSERT".equalsIgnoreCase(r.action())).toList();
                for (SiteAllocation s : data.getEditedDetailLine().getNewSiteAllocations()) {
                    c.eq(G_SITE + " | " + trim(nv.getGeneralLedger()) + " → " + trim(s.getSiteName()) + " " + plain(s.getAmount()),
                            true, containsSite(newSite, nv.getGeneralLedger(), s.getSiteName(), s.getAmount()));
                }
            }
        } else if (scenario == GeneralVoucherEditVariant.ScenarioType.PAYMENT_INFO && data.getPaymentEditTarget() != null) {
            PaymentInformation np = data.getPaymentEditTarget().getNewPayment();
            c.eq(G_DET + " | UPDATE with payment " + np.getPaymentMode().dbCode() + " " + norm(np.getInstrumentNo()), true,
                    newDet.stream().anyMatch(r -> "UPDATE".equalsIgnoreCase(r.action())
                            && np.getPaymentMode().dbCode().equalsIgnoreCase(r.str("paymenT_MODE"))
                            && java.util.Objects.equals(norm(np.getInstrumentNo()), norm(r.str("instrumenT_NUMBER")))));
        } else if (scenario == GeneralVoucherEditVariant.ScenarioType.HEADER_ONLY) {
            // Releasing on save posts the voucher, which the Audit API showed re-versioning a detail row once
            // (qaprod run 20261031_013811, FIN-GV-EDIT-HEADER-04) — allow at most that one, and only then.
            long allowedDet = releasedByEdit ? 1L : 0L;
            c.eq(G_DET + " | New versions (header-only edit" + (releasedByEdit ? ", ≤ 1 from release" : "") + ")",
                    true, (long) newDet.size() <= allowedDet);
        }
        return result(c);
    }

    private static boolean isPosted(String status) {
        return status != null && status.trim().equalsIgnoreCase("Posted");
    }

    // ───────────────────────────── Delete ─────────────────────────────

    /** A Delete writes a FINVCHMAIN DELETE equal to the last pre-delete version, a DELETE per detail line, and DELETEs for the site rows. */
    public static Result deleted(Capture before, Capture after) {
        ApiChecks c = new ApiChecks();
        state(c, after);
        List<AuditRow> newMain = after.mainAfter(before.maxVersion());
        c.eq(G_MAIN + " | DELETE versions", 1L, newMain.stream().filter(r -> "DELETE".equalsIgnoreCase(r.action())).count());
        AuditRow del = newMain.stream().filter(r -> "DELETE".equalsIgnoreCase(r.action())).findFirst().orElse(null);
        AuditRow prior = after.mainAt(before.maxVersion());
        if (del != null && prior != null) {
            Set<String> changed = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
            changed.addAll(del.changedFrom(prior).keySet());
            c.eq(G_MAIN + " | Deleted snapshot equals last version", "no differences", changed.isEmpty() ? "no differences" : String.join(", ", changed));
        }
        List<AuditRow> liveBefore = liveDetailRows(before.det());
        List<AuditRow> detDeletes = after.detAfter(before.maxVersion()).stream().filter(r -> "DELETE".equalsIgnoreCase(r.action())).toList();
        c.eq(G_DET + " | DELETE versions (lines)", (long) liveBefore.size(), (long) detDeletes.size());
        List<AuditRow> pool = new ArrayList<>(detDeletes);
        for (int i = 0; i < liveBefore.size(); i++) {
            AuditRow b = liveBefore.get(i);
            String group = "Audit · Row " + (i + 1);
            boolean debit = b.num("debiT_AMOUNT") != null && b.num("debiT_AMOUNT").signum() != 0;
            AuditRow hit = take(pool, b.str("generaL_LEDGER_NAME"), debit ? b.num("debiT_AMOUNT") : b.num("crediT_AMOUNT"), debit);
            c.title(group, "Audit · Row " + (i + 1) + " · GL: " + b.str("generaL_LEDGER_NAME") + " · " + (debit ? "Dr " : "Cr ")
                    + plain(debit ? b.num("debiT_AMOUNT") : b.num("crediT_AMOUNT")));
            c.eq(group + " | DELETE row found", true, hit != null);
        }
        List<AuditRow> siteLive = liveDetailRows(before.site());
        long siteDeletes = after.siteAfter(before.siteMaxVersion()).stream().filter(r -> "DELETE".equalsIgnoreCase(r.action())).count();
        if (!siteLive.isEmpty() || siteDeletes > 0) c.eq(G_SITE + " | DELETE versions", (long) siteLive.size(), siteDeletes);
        if (!siteLive.isEmpty() && siteDeletes == 0) {
            c.eq(G_SITE + " | Rows seen before (action@version)", "", describe(before.site()));
            c.eq(G_SITE + " | Rows seen after (action@version)", "", describe(after.site()));
        }
        return result(c);
    }

    // ───────────────────────────── helpers ─────────────────────────────

    private static String describe(List<AuditRow> rows) {
        return rows.stream().map(r -> r.action() + "@" + r.version()).collect(java.util.stream.Collectors.joining(", "));
    }

    private static void state(ApiChecks c, Capture cap) {
        c.eq(G_STATE + " | Voucher audit (FinVoucher) enabled", true, cap.voucherModuleEnabled());
        c.eq(G_STATE + " | Site Allocation audit enabled", true, cap.siteModuleEnabled());
    }

    private static Result result(ApiChecks c) {
        // Labels were written as "<group> | <check>" so one accumulator call carries the group; split them into real rows here.
        ApiChecks out = new ApiChecks();
        out.titles.putAll(c.titles);
        for (ApiCheckRow r : c.rows) {
            int bar = r.check().indexOf(" | ");
            String group = bar > 0 ? r.check().substring(0, bar) : r.group();
            String check = bar > 0 ? r.check().substring(bar + 3) : r.check();
            out.rows.add(new ApiCheckRow(group, check, r.expected(), r.actual(), r.passed()));
            String line = group + ": " + check + (r.passed() ? " = " + r.actual() : " expected " + r.expected() + " but was " + r.actual());
            (r.passed() ? out.passed : out.failed).add(line);
        }
        return new Result(out.passed, out.failed, out.rows, out.titles);
    }

    /** Detail/site rows that are not already superseded: the latest INSERT/UPDATE per row {@code code} that no DELETE follows. */
    private static List<AuditRow> liveDetailRows(List<AuditRow> history) {
        java.util.Map<String, AuditRow> latest = new java.util.LinkedHashMap<>();
        history.stream().sorted(Comparator.comparingInt(AuditRow::version)).forEach(r -> latest.put(String.valueOf(r.get("code")), r));
        return latest.values().stream().filter(r -> !"DELETE".equalsIgnoreCase(r.action())).toList();
    }

    private static AuditRow take(List<AuditRow> pool, String gl, BigDecimal amount, boolean debit) {
        for (int i = 0; i < pool.size(); i++) {
            AuditRow r = pool.get(i);
            if (trim(gl) != null && trim(gl).equals(trim(r.str("generaL_LEDGER_NAME")))
                    && sameAmount(amount, debit ? r.num("debiT_AMOUNT") : r.num("crediT_AMOUNT"))) {
                return pool.remove(i);
            }
        }
        return null;
    }

    private static boolean containsSite(List<AuditRow> rows, String gl, String site, BigDecimal amount) {
        return rows.stream().anyMatch(r -> trim(gl).equals(trim(r.str("ledgeR_NAME")))
                && (trim(site).equals(trim(r.str("transactioN_SITE_NAME"))) || trim(site).equals(trim(r.str("reF_SITE_NAME"))))
                && (sameAmount(amount, r.num("debiT_AMOUNT")) || sameAmount(amount, r.num("crediT_AMOUNT"))));
    }

    private static PaymentInformation paymentFor(MergedFinanceGeneralVoucherData data, ExpenseIncomeDetail e, int index) {
        if (data.getRowPayments() != null) {
            for (PaymentInformation p : data.getRowPayments()) {
                if (p != null && java.util.Objects.equals(p.getLineNo(), e.getLineNo())) return p;
            }
            return null;
        }
        return index == 0 ? data.getCashBankPaymentInfo() : null;
    }

    private static BigDecimal amountOf(ExpenseIncomeDetail e) {
        return e.isDebit() ? e.getExpenseDr() : e.getIncomeCr();
    }

    private static boolean sameAmount(BigDecimal a, BigDecimal b) {
        return a != null && b != null && a.compareTo(b) == 0;
    }

    private static java.time.LocalDate date(String iso) {
        return iso == null || iso.length() < 10 ? null : java.time.LocalDate.parse(iso.substring(0, 10));
    }

    private static String norm(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static String trim(String s) {
        return s == null ? null : s.trim();
    }

    private static String plain(BigDecimal v) {
        return v == null ? "(none)" : v.stripTrailingZeros().toPlainString();
    }

    private static Set<String> ci(String... names) {
        Set<String> s = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        s.addAll(List.of(names));
        return s;
    }
}
