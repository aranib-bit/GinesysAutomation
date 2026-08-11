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
import java.util.List;

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
 *     <li>Each expected {@link SiteAllocation} (site name + amount) has a matching
 *         {@code fincosttag} row keyed by {@code ref_admsite_code} — which may
 *         legitimately equal {@code admsite_code} for single-site tenants, so
 *         this never asserts they differ.</li>
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
     * @return the {@code fincosttag} rows fetched for this line (empty when none were found or
     *         the row's GL could not be resolved) — the orchestrator accumulates these across
     *         all site-applicable lines for {@code ActualResultsHolder.setTagCount}.
     */
    public List<FinCostTag> validate(SoftAssertions soft, String entcode, FinGl gl, FinVchDet detailRow,
                          ExpenseIncomeDetail row, List<SiteAllocation> expectedAllocations) {

        ReportLogger.db("FinCostTagValidator — entcode=[" + entcode + "] glcode=[" + gl.getGlcode()
                + "] (" + gl.getGlname() + "), LineNo=[" + row.getLineNo() + "]");

        // (a) gl_cc_appl mirrors site-applicability on the detail row itself
        soft.assertThat(detailRow.getGlCcAppl())
                .as("[FinCostTagValidator] finvchdet.gl_cc_appl for glcode [" + gl.getGlcode()
                        + "] entcode [" + entcode + "] — expected 'Y' (site-applicable line)")
                .isEqualToIgnoringCase("Y");

        // (b) fincosttag rows exist for this (entcode, glcode)
        List<FinCostTag> tags = finCostTagDao.findByEntcodeAndGlcode(entcode, gl.getGlcode());
        soft.assertThat(tags)
                .as("[FinCostTagValidator] fincosttag rows for entcode [" + entcode + "] glcode ["
                        + gl.getGlcode() + "] — expected at least one row (site-applicable line)")
                .isNotEmpty();
        if (tags.isEmpty()) return tags; // remaining checks are meaningless without rows

        ReportLogger.infoLabeledTable("fincosttag rows (LineNo " + row.getLineNo() + ")", buildCostTagTable(tags));

        // (c) SUM(damount)+SUM(camount) == the row's expected amount
        BigDecimal expectedAmount = row.isDebit() ? row.getExpenseDr() : row.getIncomeCr();
        BigDecimal totalDebit  = finCostTagDao.sumDebitByEntcodeAndGlcode(entcode, gl.getGlcode());
        BigDecimal totalCredit = finCostTagDao.sumCreditByEntcodeAndGlcode(entcode, gl.getGlcode());
        BigDecimal actualTotal = totalDebit.add(totalCredit);
        soft.assertThat(actualTotal)
                .as("[FinCostTagValidator] fincosttag SUM(damount)+SUM(camount)=" + actualTotal
                        + " != expected line amount=" + expectedAmount + " for entcode [" + entcode
                        + "] glcode [" + gl.getGlcode() + "] — Tagging Summary's Actual/Tagged Amount mismatch")
                .isEqualByComparingTo(expectedAmount);

        // (d) each expected SiteAllocation has a matching fincosttag row keyed by ref_admsite_code
        for (SiteAllocation expected : expectedAllocations) {
            boolean matched = tags.stream().anyMatch(tag -> {
                AdmSite taggedSite = admSiteDao.findByCode(tag.getRefAdmsiteCode());
                boolean siteMatches = taggedSite != null
                        && expected.getSiteName().equalsIgnoreCase(taggedSite.getName());
                BigDecimal tagAmount = tag.getDamount().signum() != 0 ? tag.getDamount() : tag.getCamount();
                boolean amountMatches = expected.getAmount() != null
                        && tagAmount.compareTo(expected.getAmount()) == 0;
                return siteMatches && amountMatches;
            });
            soft.assertThat(matched)
                    .as("[FinCostTagValidator] No fincosttag row matches expected SiteAllocation site=["
                            + expected.getSiteName() + "] amount=[" + expected.getAmount() + "] for entcode ["
                            + entcode + "] glcode [" + gl.getGlcode() + "] — ref_admsite_code may legitimately "
                            + "equal admsite_code for single-site tenants, this checks the amount+site pairing only")
                    .isTrue();
        }

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
        ReportLogger.infoHtml(buildLineSummary(row, gl, tags, actualTotal, expectedAllocations));
        return tags;
    }

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
                                            BigDecimal actualTotal, List<SiteAllocation> expectedAllocations) {
        StringBuilder sb = new StringBuilder();
        sb.append("<div style='font-family:monospace;font-size:12px;line-height:1.8;padding:8px 12px;")
          .append("background:#f0fff4;border-left:4px solid #38a169;border-radius:2px;margin:4px 0;'>");
        sb.append("<b>Level 4 &mdash; fincosttag site allocation (LineNo ").append(esc(row.getLineNo())).append(")</b><br/>");
        sb.append("&#10003;&nbsp; GL: <b>").append(esc(gl.getGlname())).append("</b><br/>");
        sb.append("&#10003;&nbsp; Tagged rows: <b>").append(tags.size()).append("</b><br/>");
        sb.append("&#10003;&nbsp; SUM(damount)+SUM(camount) = <b>").append(actualTotal).append("</b><br/>");
        for (SiteAllocation sa : expectedAllocations) {
            sb.append("&#10003;&nbsp; Site allocation: <b>").append(esc(sa.getSiteName()))
              .append("</b> = ").append(esc(sa.getAmount())).append("<br/>");
        }
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
