package com.gsl.validation.db.finance;

import com.gsl.database.DatabaseManager;
import com.gsl.database.dao.adm.AdmSiteDao;
import com.gsl.database.dao.finance.FinCostTagDao;
import com.gsl.database.dao.finance.FinSlDao;
import com.gsl.enums.Finance.Operations.GeneralVoucher.GlLineType;
import com.gsl.model.adm.AdmSite;
import com.gsl.model.finance.FinCostTag;
import com.gsl.model.finance.FinGl;
import com.gsl.model.finance.FinSl;
import com.gsl.model.finance.FinVchDet;
import com.gsl.reports.ReportLogger;
import com.gsl.testdata.Finance.Operations.Vouchers.GeneralVoucher.ExpenseIncomeDetail;
import com.gsl.testdata.Finance.Operations.Vouchers.GeneralVoucher.SiteAllocation;
import org.assertj.core.api.SoftAssertions;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Validates a General Voucher's site-allocation persistence in {@code fincosttag}
 * (see plan §2.1/§2.2) for one Type2/Type3 {@link ExpenseIncomeDetail} row.
 *
 * <p>Checks, per row:
 * <ol>
 *     <li>{@code finvchdet.gl_cc_appl = 'Y'} — cheap secondary signal, denormalized
 *         straight onto the detail row (confirmed live, cross-tenant).</li>
 *     <li>{@code fincosttag} rows exist for {@code (entcode, glcode)}.</li>
 *     <li>{@code SUM(damount)+SUM(camount)} across those rows equals the row's
 *         expected amount (== Allocate Sites' Tagging Summary "Tagged Amount").</li>
 *     <li>Row COUNT equals the expected {@link SiteAllocation} count — otherwise a
 *         missing/extra row could hide behind a duplicate-valued expected entry
 *         (see next point).</li>
 *     <li>Each expected {@link SiteAllocation} (site name + amount) has a distinct,
 *         1:1-matching {@code fincosttag} row keyed by {@code ref_admsite_code} —
 *         which may legitimately equal {@code admsite_code} for single-site tenants,
 *         so this never asserts they differ. Matching consumes the actual row it
 *         pairs with, so two allocations that share the same {@code glcode}/amount
 *         but a different {@code ref_admsite_code} (e.g. one line split evenly
 *         across 2 sites) can never both be satisfied by the same single row.</li>
 *     <li>The full, sorted list of {@code (glcode, site, amount)} triples the
 *         expected {@link SiteAllocation}s describe equals the full, sorted list
 *         actually found in {@code fincosttag} (site resolved from {@code
 *         ref_admsite_code}) — an explicit, at-a-glance list-vs-list cross-check
 *         on top of the previous point's per-allocation pairing, also rendered
 *         in the "Actual Results" summary box so a reader can see both lists
 *         side by side.</li>
 *     <li>For Type3 rows, {@code slcode} matches the expected Sub Ledger's
 *         {@code slcode} on every {@code fincosttag} row.</li>
 * </ol>
 *
 * <p>NON-BLOCKING: all failures collected via {@link SoftAssertions}, mirroring
 * {@code FinTagValidator}'s pattern.
 */
public class FinCostTagValidator {

    private final FinCostTagDao finCostTagDao;
    private final AdmSiteDao admSiteDao;
    private final FinSlDao finSlDao;

    public FinCostTagValidator() {
        this.finCostTagDao = DatabaseManager.getJdbi().onDemand(FinCostTagDao.class);
        this.admSiteDao    = DatabaseManager.getJdbi().onDemand(AdmSiteDao.class);
        this.finSlDao       = DatabaseManager.getJdbi().onDemand(FinSlDao.class);
    }

    /**
     * @param postcode the resolved {@code finpost} per-line (VDG) posting row's own postcode for
     *                 THIS line — the unique key that actually scopes {@code fincosttag} rows to
     *                 this specific line. Bug fix (2026-09): previously scoped by
     *                 {@code (entcode, glcode)} alone, which silently pooled together the
     *                 fincosttag rows of every site-applicable line sharing that {@code glcode}
     *                 within one voucher (e.g. two different Expense/Income Detail lines both
     *                 posting to the same GL account) — the SUM(damount)+SUM(camount) check then
     *                 compared this line's own expected amount against the COMBINED total of
     *                 every line sharing the GL, and the "unallocated"/matched checks below mixed
     *                 in the wrong rows too. {@code postcode} is unique per finpost row, so this
     *                 can no longer collide.
     * @return the {@code fincosttag} rows fetched for this line (empty when none were found or
     *         the row's GL could not be resolved) — the orchestrator accumulates these across
     *         all site-applicable lines for {@code ActualResultsHolder.setTagCount}.
     */
    public List<FinCostTag> validate(SoftAssertions soft, String entcode, FinGl gl, FinVchDet detailRow,
                          ExpenseIncomeDetail row, List<SiteAllocation> expectedAllocations, long postcode) {

        ReportLogger.db("FinCostTagValidator — entcode=[" + entcode + "] glcode=[" + gl.getGlcode()
                + "] (" + gl.getGlname() + "), postcode=[" + postcode + "], LineNo=[" + row.getLineNo() + "]");

        // (a) gl_cc_appl mirrors site-applicability on the detail row itself
        soft.assertThat(detailRow.getGlCcAppl())
                .as("[FinCostTagValidator] finvchdet.gl_cc_appl for glcode [" + gl.getGlcode()
                        + "] entcode [" + entcode + "] — expected 'Y' (site-applicable line)")
                .isEqualToIgnoringCase("Y");

        // (b) fincosttag rows exist for THIS line's own postcode — not just (entcode, glcode),
        // which would also match every OTHER line sharing the same GL account (see Javadoc).
        List<FinCostTag> tags = finCostTagDao.findByPostcode(postcode);
        soft.assertThat(tags)
                .as("[FinCostTagValidator] fincosttag rows for entcode [" + entcode + "] glcode ["
                        + gl.getGlcode() + "] postcode [" + postcode
                        + "] — expected at least one row (site-applicable line)")
                .isNotEmpty();
        if (tags.isEmpty()) return tags; // remaining checks are meaningless without rows

        // (b2) exact row-count match against the expected SiteAllocation count — closes a gap
        // check (d) alone can't: `anyMatch` there only proves "some row satisfies this expected
        // allocation," never that every expected allocation maps to a DISTINCT actual row, nor
        // that no extra/unaccounted-for row exists. Two allocations for the same line sharing the
        // same glcode/damount-or-camount but a different ref_admsite_code (e.g. an even split
        // across 2 sites, 500/500) are still correctly told apart by site name in (d) — but
        // without this count check, a missing row could hide behind a duplicate-valued expected
        // entry being satisfied twice by the same single actual row, or an extra stray row could
        // go completely unverified. Asserted before (d) so a count mismatch is reported plainly,
        // not just as a confusing chain of individual site/amount mismatches.
        soft.assertThat(tags.size())
                .as("[FinCostTagValidator] fincosttag row COUNT for entcode [" + entcode + "] glcode ["
                        + gl.getGlcode() + "] postcode [" + postcode + "] — expected exactly "
                        + expectedAllocations.size() + " row(s) (one per SiteAllocation), found " + tags.size())
                .isEqualTo(expectedAllocations.size());

        ReportLogger.infoLabeledTable("fincosttag rows (LineNo " + row.getLineNo() + ")", buildCostTagTable(tags));

        // (c) SUM(damount)+SUM(camount) == the row's expected amount — summed in Java from the
        // already-fetched, correctly-scoped `tags` rather than 2 more DB round-trips against the
        // old (entcode, glcode) scoping (which would reintroduce the exact cross-line pooling bug
        // this fix removes).
        BigDecimal expectedAmount = row.isDebit() ? row.getExpenseDr() : row.getIncomeCr();
        BigDecimal totalDebit = tags.stream().map(FinCostTag::getDamount).filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalCredit = tags.stream().map(FinCostTag::getCamount).filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal actualTotal = totalDebit.add(totalCredit);
        soft.assertThat(actualTotal)
                .as("[FinCostTagValidator] fincosttag SUM(damount)+SUM(camount)=" + actualTotal
                        + " != expected line amount=" + expectedAmount + " for entcode [" + entcode
                        + "] glcode [" + gl.getGlcode() + "] — Tagging Summary's Actual/Tagged Amount mismatch")
                .isEqualByComparingTo(expectedAmount);

        // (d) each expected SiteAllocation has a matching fincosttag row keyed by ref_admsite_code
        // — a true 1:1 pairing, not just "does at least one row satisfy this," so two allocations
        // sharing the same glcode/damount-or-camount but a DIFFERENT ref_admsite_code (e.g. an
        // even 500/500 split of one line across 2 sites — the site name alone still tells them
        // apart) can never let one actual row silently satisfy two expected entries at once. A
        // matched row is removed from `remaining` immediately, so a duplicate-valued expected
        // allocation (same site+amount twice) genuinely requires 2 distinct actual rows, not one
        // row counted twice — the (b2) count check above already guarantees the sizes agree, this
        // guarantees the CONTENT actually pairs up 1:1, not just totals to the right count.
        List<FinCostTag> remaining = new ArrayList<>(tags);
        for (SiteAllocation expected : expectedAllocations) {
            FinCostTag matchedTag = remaining.stream().filter(tag -> {
                AdmSite taggedSite = admSiteDao.findByCode(tag.getRefAdmsiteCode());
                boolean siteMatches = taggedSite != null
                        && expected.getSiteName().equalsIgnoreCase(taggedSite.getName());
                BigDecimal tagAmount = tag.getDamount().signum() != 0 ? tag.getDamount() : tag.getCamount();
                boolean amountMatches = expected.getAmount() != null
                        && tagAmount.compareTo(expected.getAmount()) == 0;
                return siteMatches && amountMatches;
            }).findFirst().orElse(null);
            soft.assertThat(matchedTag)
                    .as("[FinCostTagValidator] No UNMATCHED fincosttag row pairs with expected SiteAllocation site=["
                            + expected.getSiteName() + "] amount=[" + expected.getAmount() + "] for entcode ["
                            + entcode + "] glcode [" + gl.getGlcode() + "] — ref_admsite_code may legitimately "
                            + "equal admsite_code for single-site tenants, this checks the amount+site pairing only "
                            + "(already-matched rows are excluded, so one row can't satisfy two expected allocations)")
                    .isNotNull();
            if (matchedTag != null) {
                remaining.remove(matchedTag);
            }
        }

        // (d2) explicit (glcode, site, amount) LIST equality — same content (d)'s consumption-based
        // pairing already proves one allocation at a time, but surfaced here as one side-by-side,
        // sorted "expected list" vs "actual list" comparison so a reader (or the failure message
        // itself) can see the whole site-allocation picture at a glance, not just a chain of
        // per-allocation pass/fail lines. Site NAMES are resolved from ref_admsite_code the same
        // way (d) does — this is a genuine cross-check with amount AND glcode folded into the same
        // comparable key, not a re-statement of (b2)'s bare count.
        List<String> expectedList = expectedAllocations.stream()
                .map(sa -> compositeKey(gl.getGlcode(), sa.getSiteName(), sa.getAmount()))
                .sorted()
                .collect(Collectors.toList());
        List<String> actualList = tags.stream()
                .map(tag -> {
                    AdmSite taggedSite = admSiteDao.findByCode(tag.getRefAdmsiteCode());
                    String siteName = taggedSite != null ? taggedSite.getName()
                            : "UNRESOLVED(code=" + tag.getRefAdmsiteCode() + ")";
                    BigDecimal tagAmount = tag.getDamount().signum() != 0 ? tag.getDamount() : tag.getCamount();
                    return compositeKey(tag.getGlcode(), siteName, tagAmount);
                })
                .sorted()
                .collect(Collectors.toList());
        soft.assertThat(actualList)
                .as("[FinCostTagValidator] fincosttag site-allocation list (glcode+site+amount) for entcode ["
                        + entcode + "] glcode [" + gl.getGlcode() + "] postcode [" + postcode
                        + "] — expected " + expectedList + " but actual " + actualList)
                .isEqualTo(expectedList);

        // (e) Type3 rows: slcode matches the expected Sub Ledger on every fincosttag row
        if (row.getResolvedGlType() == GlLineType.TYPE3_SITE_AND_SUBLEDGER) {
            FinSl expectedSl = (row.getSubLedgerId() != null && !row.getSubLedgerId().isBlank())
                    ? finSlDao.findBySlnameAndSlid(row.getSubLedger(), row.getSubLedgerId())
                    : finSlDao.findBySlname(row.getSubLedger()).stream().findFirst().orElse(null);

            soft.assertThat(expectedSl)
                    .as("[FinCostTagValidator] Could not resolve expected Sub Ledger '" + row.getSubLedger()
                            + "' via finsl for Type3 row LineNo [" + row.getLineNo() + "]")
                    .isNotNull();

            if (expectedSl != null) {
                for (FinCostTag tag : tags) {
                    soft.assertThat(tag.getSlcode())
                            .as("[FinCostTagValidator] fincosttag.slcode=" + tag.getSlcode()
                                    + " != expected Sub Ledger slcode=" + expectedSl.getSlcode()
                                    + " for entcode [" + entcode + "] glcode [" + gl.getGlcode() + "]")
                            .isEqualTo(expectedSl.getSlcode());
                }
                soft.assertThat(detailRow.getSlcode())
                        .as("[FinCostTagValidator] finvchdet.slcode=" + detailRow.getSlcode()
                                + " != expected Sub Ledger slcode=" + expectedSl.getSlcode()
                                + " for entcode [" + entcode + "] glcode [" + gl.getGlcode() + "]")
                        .isEqualTo(expectedSl.getSlcode());
            }
        }

        ReportLogger.db("FinCostTagValidator — " + tags.size() + " row(s) validated for glcode ["
                + gl.getGlcode() + "], total tagged=" + actualTotal);
        // Natural-cased (not the normalized comparison key) actual site display list — reuses the
        // SAME per-row site resolution as (d2) above, just formatted for a human reader rather than
        // for string equality.
        List<String> actualSiteDisplay = tags.stream()
                .map(tag -> {
                    AdmSite taggedSite = admSiteDao.findByCode(tag.getRefAdmsiteCode());
                    String siteName = taggedSite != null ? taggedSite.getName()
                            : "UNRESOLVED(code=" + tag.getRefAdmsiteCode() + ")";
                    BigDecimal tagAmount = tag.getDamount().signum() != 0 ? tag.getDamount() : tag.getCamount();
                    return siteName + " = " + tagAmount;
                })
                .collect(Collectors.toList());
        ReportLogger.infoHtml(buildLineSummary(row, gl, tags, actualTotal, expectedAllocations, actualSiteDisplay));
        return tags;
    }

    /** Normalized "glcode=X, site=Y, amount=Z" key used by check (d2) — site name is
     *  upper-cased/trimmed and amount is fixed to a 2-decimal scale so an expected and actual
     *  entry describing the same allocation always produce the IDENTICAL string, regardless of
     *  the raw casing/precision each side happens to carry. */
    private static String compositeKey(long glcode, String siteName, BigDecimal amount) {
        String normalizedSite = siteName == null ? "—" : siteName.trim().toUpperCase(Locale.ROOT);
        BigDecimal safeAmount = amount == null ? BigDecimal.ZERO : amount;
        return "glcode=" + glcode + ", site=" + normalizedSite
                + ", amount=" + safeAmount.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    /** Builds the {@code fincosttag} rows' display table for the Extent Report. */
    private static String[][] buildCostTagTable(List<FinCostTag> rows) {
        String[] headers = {"postcode", "entcode", "glcode", "slcode", "damount", "camount", "ref_admsite_code", "admsite_code"};
        String[][] table = new String[rows.size() + 1][headers.length];
        table[0] = headers;
        for (int i = 0; i < rows.size(); i++) {
            FinCostTag r = rows.get(i);
            table[i + 1] = new String[]{
                str(r.getPostcode()),        str(r.getEntcode()),        str(r.getGlcode()),
                str(r.getSlcode()),          str(r.getDamount()),        str(r.getCamount()),
                str(r.getRefAdmsiteCode()),  str(r.getAdmsiteCode())
            };
        }
        return table;
    }

    /** Mirrors {@code FinTagValidator.buildLevel4aSummary}'s green-checklist-box shape,
     *  applied to fincosttag's site-allocation checks (a structurally different table from
     *  AP/AR's fintag, so the checks listed here are GV-specific, not a relabeled copy). */
    private static String buildLineSummary(ExpenseIncomeDetail row, FinGl gl, List<FinCostTag> tags,
                                            BigDecimal actualTotal, List<SiteAllocation> expectedAllocations,
                                            List<String> actualSiteDisplay) {
        StringBuilder sb = new StringBuilder();
        sb.append("<div style='font-family:monospace;font-size:12px;line-height:1.8;padding:8px 12px;")
          .append("background:#f0fff4;border-left:4px solid #38a169;border-radius:2px;margin:4px 0;'>");
        sb.append("<b>Level 4 &mdash; fincosttag site allocation (LineNo ").append(esc(row.getLineNo())).append(")</b><br/>");
        sb.append("&#10003;&nbsp; GL: <b>").append(esc(gl.getGlname())).append("</b><br/>");
        sb.append("&#10003;&nbsp; Tagged rows: <b>").append(tags.size()).append("</b> (expected <b>")
          .append(expectedAllocations.size()).append("</b>)<br/>");
        sb.append("&#10003;&nbsp; SUM(damount)+SUM(camount) = <b>").append(actualTotal).append("</b><br/>");
        sb.append("&#10003;&nbsp; Expected site allocations: <b>");
        for (int i = 0; i < expectedAllocations.size(); i++) {
            SiteAllocation sa = expectedAllocations.get(i);
            if (i > 0) sb.append(", ");
            sb.append(esc(sa.getSiteName())).append(" = ").append(esc(sa.getAmount()));
        }
        sb.append("</b><br/>");
        sb.append("&#10003;&nbsp; Actual fincosttag site allocations (via ref_admsite_code): <b>")
          .append(esc(String.join(", ", actualSiteDisplay))).append("</b><br/>");
        sb.append("</div>");
        return sb.toString();
    }

    private static String esc(Object o) {
        if (o == null) return "&mdash;";
        return o.toString().replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static String str(Object o) { return o == null ? "—" : o.toString(); }
    private static String str(long v)   { return String.valueOf(v); }
}
