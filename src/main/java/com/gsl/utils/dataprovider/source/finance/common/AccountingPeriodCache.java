package com.gsl.utils.dataprovider.source.finance.common;

import com.gsl.database.dao.adm.AdmMonthDao;
import com.gsl.database.dao.adm.AdmOuMonthDao;
import com.gsl.enums.PeriodDomain;
import com.gsl.model.adm.AdmMonth;
import com.gsl.model.adm.AdmOuMonth;
import com.gsl.model.finance.FinVchMain;
import org.jdbi.v3.core.Jdbi;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Per-scan memoizing wrapper around {@link AccountingPeriodResolver}'s hottest lookups
 * ({@code resolveOuCode}/{@code isYearOpen}/the {@code admmonth}+{@code admou_month} pair backing
 * {@code isMonthOpen}/{@code isEditable}). Introduced because every candidate-claim scan (GV/AR
 * Edit/Revert/Delete/Release's own {@code claimOne*}/{@code scanForClaim}/{@code scanAndClaim}
 * methods) calls {@link AccountingPeriodResolver#isEditable} once per REJECTED candidate, and
 * within one scan the resolved OU/year/month-calendar data is identical for every candidate
 * sharing the same site/year/calendar-month — confirmed live via a run where the same 4 queries
 * ({@code admmonth}, {@code admou_month}, {@code admsite_ou}, {@code admou_year}) repeated
 * hundreds of times with identical bind values while only the candidate's own {@code vchcode}
 * varied.
 *
 * <p>Deliberately NOT folded into {@link AccountingPeriodResolver} itself (which stays fully
 * static/stateless — every one of its 20+ existing callers is unaffected). This is a separate,
 * explicitly-scoped, non-thread-shared instance: callers {@code new} one fresh at the top of each
 * scan method and let it go out of scope when the scan ends, so there is no risk of one scan's
 * cached "year is open" answer leaking into a later, unrelated scan — accounting-period status is
 * effectively static within a single scan's short lifetime, but is deliberately NOT assumed static
 * across separate test-data-provider calls or threads.
 *
 * <p>{@link #isMonthOpen} duplicates (rather than reuses) the tiny month-containment filter
 * {@link AccountingPeriodResolver}'s own private {@code findContainingMonth} already implements
 * (same inclusive-both-ends {@code atStartOfDay()} rule), so that class needs zero changes — this
 * cache is purely additive.
 */
public final class AccountingPeriodCache {

    private final Map<Long, Long> ouCodeBySite = new HashMap<>();
    private final Map<YearOuKey, Boolean> yearOpenByYcodeOuCode = new HashMap<>();
    private final Map<Long, List<AdmMonth>> monthsByYcode = new HashMap<>();
    private final Map<MonthOuKey, Optional<AdmOuMonth>> ouMonthByMcodeOuCode = new HashMap<>();

    /** Cached {@link AccountingPeriodResolver#resolveOuCode}. */
    public long resolveOuCode(Jdbi jdbi, long admsiteCodeOwner) {
        return ouCodeBySite.computeIfAbsent(admsiteCodeOwner,
                code -> AccountingPeriodResolver.resolveOuCode(jdbi, code));
    }

    /** Cached {@link AccountingPeriodResolver#isYearOpen}. */
    public boolean isYearOpen(Jdbi jdbi, long ycode, long ouCode) {
        return yearOpenByYcodeOuCode.computeIfAbsent(new YearOuKey(ycode, ouCode),
                key -> AccountingPeriodResolver.isYearOpen(jdbi, ycode, ouCode));
    }

    /**
     * Cached equivalent of {@link AccountingPeriodResolver#isMonthOpen} — reuses the cached
     * {@code admmonth} list for {@code ycode} (one query per distinct {@code ycode} this cache
     * instance ever sees, not one per candidate) and the cached {@code admou_month} row for the
     * resolved month's {@code mcode}+{@code ouCode} (one query per distinct calendar month, not
     * one per candidate).
     */
    public boolean isMonthOpen(Jdbi jdbi, LocalDate date, long ycode, long ouCode, PeriodDomain domain) {
        List<AdmMonth> months = monthsByYcode.computeIfAbsent(ycode,
                yc -> jdbi.onDemand(AdmMonthDao.class).findByYcode(yc));
        Optional<AdmMonth> month = months.stream()
                .filter(m -> !date.atStartOfDay().isBefore(m.getDtfr()) && !date.atStartOfDay().isAfter(m.getDtto()))
                .findFirst();
        if (month.isEmpty()) return false;

        long mcode = month.get().getMcode();
        Optional<AdmOuMonth> ouMonth = ouMonthByMcodeOuCode.computeIfAbsent(new MonthOuKey(mcode, ouCode),
                key -> jdbi.onDemand(AdmOuMonthDao.class).findByMonthAndOu(mcode, ouCode));
        return ouMonth.map(domain::statusOf).map("O"::equalsIgnoreCase).orElse(false);
    }

    /** Cached equivalent of {@link AccountingPeriodResolver#isEditable} — same composition rule. */
    public boolean isEditable(Jdbi jdbi, FinVchMain header, PeriodDomain domain) {
        long ouCode = resolveOuCode(jdbi, header.getAdmsiteCodeOwner());
        if (!isYearOpen(jdbi, header.getYcode(), ouCode)) return false;
        return isMonthOpen(jdbi, header.getVchdt(), header.getYcode(), ouCode, domain);
    }

    private record YearOuKey(long ycode, long ouCode) {}

    private record MonthOuKey(long mcode, long ouCode) {}
}
