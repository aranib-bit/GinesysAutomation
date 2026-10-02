package com.gsl.utils.dataprovider.source.finance.common;

import com.gsl.database.dao.adm.AdmMonthDao;
import com.gsl.database.dao.adm.AdmOuMonthDao;
import com.gsl.database.dao.adm.AdmOuYearDao;
import com.gsl.database.dao.adm.AdmSiteOuDao;
import com.gsl.database.dao.adm.AdmYearDao;
import com.gsl.enums.PeriodDomain;
import com.gsl.model.adm.AdmMonth;
import com.gsl.model.adm.AdmOuMonth;
import com.gsl.model.adm.AdmOuYear;
import com.gsl.model.adm.AdmYear;
import com.gsl.model.finance.FinVchMain;
import com.gsl.reports.VoucherReportUtils;
import com.gsl.utils.dataprovider.fixture.finance.VoucherEditDateVariant;
import org.jdbi.v3.core.Jdbi;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Domain-agnostic accounting-year/financial-period utility, shared by every voucher domain's
 * Edit-flow test-data generation (GV/AR/AP — see the "GV Edit: accounting-year-aware dates..."
 * plan). New package (this one) under the existing flat {@code .../source/finance/} folder
 * signals "shared across domains", mirroring how {@code VoucherListPageBase} already is for page
 * objects.
 *
 * <p>Every boundary/open-check here derives from the EXACT rule the live app's own validator
 * already encodes ({@code FinVchMainBaseValidator}: a date is in-range for a fiscal year iff
 * {@code !date.atStartOfDay().isBefore(dtfr) && !date.atStartOfDay().isAfter(dtto)} — inclusive
 * both ends, no timezone conversion) — test-data generation must match this precisely, or a
 * genuinely valid-looking date still gets rejected by the app.
 */
public final class AccountingPeriodResolver {

    private AccountingPeriodResolver() {}

    /**
     * Resolves the OU code ({@code admou.code}) that governs period-closing checks for a
     * voucher's owner site, via {@code admsite_ou}. Live-verified deterministic (one
     * {@code admsite_code} maps to exactly one {@code admou_code}) for this tenant; throws if the
     * mapping is ever missing — a genuinely broken candidate, not a business scenario to silently
     * tolerate.
     */
    public static long resolveOuCode(Jdbi jdbi, long admsiteCodeOwner) {
        List<Long> ouCodes = jdbi.onDemand(AdmSiteOuDao.class).findOuCodesByAdmsiteCode(admsiteCodeOwner);
        if (ouCodes.isEmpty()) {
            throw new IllegalStateException(
                    "[AccountingPeriodResolver] admsite_ou has no admou_code for admsite_code=" + admsiteCodeOwner);
        }
        return ouCodes.get(0);
    }

    /**
     * {@code true} iff the OU's own accounting year is Open ({@code admyear_status='O'}). Absence
     * of an {@code admou_year} row is treated as NOT open — fail safe, since an unconfigured
     * OU/year combination isn't something a positive test should silently proceed against.
     */
    public static boolean isYearOpen(Jdbi jdbi, long ycode, long ouCode) {
        Optional<AdmOuYear> ouYear = jdbi.onDemand(AdmOuYearDao.class).findByYcodeAndOu(ycode, ouCode);
        return ouYear.map(y -> "O".equalsIgnoreCase(y.getAdmyearStatus())).orElse(false);
    }

    /**
     * Resolves the {@link AdmYear} that CONTAINS {@code date} and is open for {@code ouCode} —
     * purely date-based (never derives anything from a document's {@code vchcode}, which is only
     * an insert-order surrogate key with no guaranteed relationship to accounting period or
     * document date — a backdated/corrective entry can carry a high vchcode but an old
     * {@code ycode}/{@code vchdt}; see the "GV Revert picks documents in a now-closed accounting
     * period, + closed-period negative-test coverage" plan, follow-up round 2). Used by
     * candidate-claim methods to prefer the CURRENT year's documents over older-but-still-open
     * ones, addressing the "why is an older year's document being picked when the current year
     * has plenty" gap. Same inclusive-both-ends {@code atStartOfDay()} rule
     * {@link #findContainingMonth} already uses. Empty (fail-safe, same convention as
     * {@link #isYearOpen}/{@link #isMonthOpen}) if {@code date} falls in a gap in the
     * {@code admyear} calendar, or its containing year is closed.
     */
    public static Optional<AdmYear> findCurrentYear(Jdbi jdbi, LocalDate date, long ouCode) {
        return findCurrentYear(jdbi, date, ouCode, null);
    }

    /**
     * Cache-aware overload of {@link #findCurrentYear} — routes each candidate year's
     * {@code isYearOpen} check through {@code cache} (the caller's own {@link AccountingPeriodCache})
     * instead of the raw static query, when a non-null cache is supplied. {@code null} preserves the
     * original uncached behavior exactly (used by the 3-arg overload above).
     */
    public static Optional<AdmYear> findCurrentYear(Jdbi jdbi, LocalDate date, long ouCode, AccountingPeriodCache cache) {
        List<AdmYear> years = jdbi.onDemand(AdmYearDao.class).findAll();
        return years.stream()
                .filter(y -> !date.atStartOfDay().isBefore(y.getDtfr()) && !date.atStartOfDay().isAfter(y.getDtto()))
                .filter(y -> cache != null ? cache.isYearOpen(jdbi, y.getYcode(), ouCode) : isYearOpen(jdbi, y.getYcode(), ouCode))
                .findFirst();
    }

    /**
     * Resolves today's accounting year (via {@link #findCurrentYear}, using the OU of the first
     * candidate — OU is consistent per tenant in practice, and any candidate's header is sufficient
     * to resolve it), appends a diagnostic line to {@code gatheringLog} describing what was found,
     * and returns the resolved {@code ycode} (or {@code null} if {@code candidates} is empty, or
     * today falls outside every open year). Shared by every candidate-claim data source that wants
     * to prefer the current year over an older-but-open one (GV Revert/Edit/Delete, AR Edit) — see
     * the "GV Revert picks documents in a now-closed accounting period, + closed-period
     * negative-test coverage" plan, follow-up round 2.
     *
     * <p><b>Cache-aware</b> (Round 12 continued, Finding C): {@code cache} — the SAME
     * {@link AccountingPeriodCache} instance the caller's own claim-scan uses for its
     * {@code isEditable} checks — MUST be created BEFORE calling this method, not after (every call
     * site used to create its cache afterward). Routing this lookup through that cache means the
     * first candidate's OU/year data, once resolved here, is already cached when the scan's own
     * {@code isEditable} check evaluates that same candidate moments later, instead of being
     * re-queried — confirmed live via {@code admsite_ou}/{@code admou_year} firing twice in a row
     * for the first candidate in a scan.
     *
     * <p>{@code candidates} is now a bulk-fetched {@code List<FinVchMain>} (Round 12's own N+1 fix
     * — see {@code DbGeneralVoucherDeleteDataSource}/{@code DbGeneralVoucherRevertDataSource}/
     * {@code DbGeneralVoucherEditDataSource}'s claim-scan SQL), so this method no longer needs a
     * {@code FinVchMainDao} to re-fetch the first candidate's header — it's already in hand.
     */
    public static Long logAndResolveCurrentYcode(Jdbi jdbi, List<FinVchMain> candidates,
            List<String> gatheringLog, AccountingPeriodCache cache) {
        LocalDate today = LocalDate.now();
        if (candidates.isEmpty()) {
            gatheringLog.add("  Current date: " + today.format(VoucherReportUtils.DATE_FMT)
                    + " — no candidate available to resolve an OU from; no current-year preference applied");
            return null;
        }

        FinVchMain header = candidates.get(0);
        long ouCode = cache.resolveOuCode(jdbi, header.getAdmsiteCodeOwner());
        Optional<AdmYear> currentYear = findCurrentYear(jdbi, today, ouCode, cache);
        if (currentYear.isPresent()) {
            AdmYear y = currentYear.get();
            gatheringLog.add("  Current date: " + today.format(VoucherReportUtils.DATE_FMT)
                    + " — falls within admyear ycode=" + y.getYcode() + " '" + y.getYname() + "' ("
                    + firstValidDay(y).format(VoucherReportUtils.DATE_FMT)
                    + " to " + lastValidDay(y).format(VoucherReportUtils.DATE_FMT)
                    + ") for admou_code=" + ouCode + " — preferring this year's candidates first");
            return y.getYcode();
        }
        gatheringLog.add("  Current date: " + today.format(VoucherReportUtils.DATE_FMT)
                + " — no open admyear found containing today for admou_code=" + ouCode
                + "; falling back to any open year");
        return null;
    }

    /**
     * The last calendar date still inside {@code year} — always {@code dtto}'s own calendar date,
     * since {@code dtto}'s time-of-day is same-day (>= 00:00), so that day's own midnight never
     * exceeds it, while the following day's midnight always would.
     */
    public static LocalDate lastValidDay(AdmYear year) {
        return year.getDtto().toLocalDate();
    }

    /**
     * The first calendar date inside {@code year} — {@code dtfr}'s own calendar date only if
     * {@code dtfr}'s time-of-day is exactly midnight; otherwise the day AFTER, since that day's own
     * midnight would be before {@code dtfr}. General — not hardcoded to any one observed offset.
     */
    public static LocalDate firstValidDay(AdmYear year) {
        LocalDate dtfrDate = year.getDtfr().toLocalDate();
        boolean dtfrIsMidnight = year.getDtfr().toLocalTime().equals(LocalTime.MIDNIGHT);
        return dtfrIsMidnight ? dtfrDate : dtfrDate.plusDays(1);
    }

    /**
     * Finds the {@link AdmMonth} row (within {@code ycode}) that contains {@code date}, using the
     * same inclusive-both-ends rule as the year-level check.
     */
    private static Optional<AdmMonth> findContainingMonth(Jdbi jdbi, LocalDate date, long ycode) {
        List<AdmMonth> months = jdbi.onDemand(AdmMonthDao.class).findByYcode(ycode);
        return months.stream()
                .filter(m -> !date.atStartOfDay().isBefore(m.getDtfr()) && !date.atStartOfDay().isAfter(m.getDtto()))
                .findFirst();
    }

    /**
     * {@code true} iff {@code date}'s containing month is open for {@code domain} at
     * {@code ouCode}. A date whose containing month can't even be found (a gap in the
     * {@code admmonth} calendar) is treated as NOT open — fail safe, matching
     * {@link #isYearOpen}'s convention.
     */
    public static boolean isMonthOpen(Jdbi jdbi, LocalDate date, long ycode, long ouCode, PeriodDomain domain) {
        Optional<AdmMonth> month = findContainingMonth(jdbi, date, ycode);
        if (month.isEmpty()) return false;
        Optional<AdmOuMonth> ouMonth = jdbi.onDemand(AdmOuMonthDao.class)
                .findByMonthAndOu(month.get().getMcode(), ouCode);
        return ouMonth.map(domain::statusOf).map("O"::equalsIgnoreCase).orElse(false);
    }

    /**
     * The candidate-selection guard: {@code true} iff {@code header}'s own accounting year is open
     * AND its own {@code vchdt}'s month is open — i.e. this document is actually editable at all,
     * independent of what new date a test might try to assign it.
     */
    public static boolean isEditable(Jdbi jdbi, FinVchMain header, PeriodDomain domain) {
        long ouCode = resolveOuCode(jdbi, header.getAdmsiteCodeOwner());
        if (!isYearOpen(jdbi, header.getYcode(), ouCode)) return false;
        return isMonthOpen(jdbi, header.getVchdt(), header.getYcode(), ouCode, domain);
    }

    /**
     * {@code true} only for a fiscal year still "in progress" — i.e. it has room after today.
     * Needed to construct the {@link VoucherEditDateVariant#FUTURE_DATE} scenario (future date,
     * still same accounting year): a past/fully-elapsed year would make "date > today"
     * indistinguishable from simply crossing into a different (later) year.
     */
    public static boolean supportsFutureDateVariant(AdmYear year) {
        return lastValidDay(year).isAfter(LocalDate.now());
    }

    /**
     * Resolves the actual {@link LocalDate} to assign as an Edit test's new document date, for the
     * given {@link VoucherEditDateVariant}. {@code ycode}/{@code ouCode} identify the claimed voucher's own
     * fiscal year/OU.
     */
    public static LocalDate resolveEditDate(Jdbi jdbi, long ycode, long ouCode, VoucherEditDateVariant variant, PeriodDomain domain) {
        AdmYear year = jdbi.onDemand(AdmYearDao.class).findByYcode(ycode)
                .orElseThrow(() -> new IllegalStateException(
                        "[AccountingPeriodResolver] admyear has no row for ycode=" + ycode));

        return switch (variant) {
            case FIRST_DAY -> firstValidDay(year);
            case LAST_DAY -> lastValidDay(year);
            // Capped at today by design, to stay a distinct "typical mid-year" case from the
            // dedicated FUTURE_DATE variant below — NOT because a later date would be rejected;
            // confirmed live the app accepts future-dated documents within the same accounting year.
            case RANDOM_MID -> randomOpenDate(jdbi, year, ouCode, domain,
                    firstValidDay(year).plusDays(1), minDate(lastValidDay(year), LocalDate.now()).minusDays(1));
            // Deterministically just outside the year — negative case: wrong accounting year.
            case CROSS_YEAR_INVALID -> lastValidDay(year).plusDays(1);
            // Still inside the SAME year, but after today — deliberately exercises the app's
            // confirmed ACCEPTANCE of future-dated documents (positive case, not a rejection).
            // Only ever invoked by a caller that already confirmed supportsFutureDateVariant(year).
            case FUTURE_DATE -> randomOpenDate(jdbi, year, ouCode, domain,
                    LocalDate.now().plusDays(1), lastValidDay(year));
        };
    }

    private static LocalDate minDate(LocalDate a, LocalDate b) {
        return a.isBefore(b) ? a : b;
    }

    /**
     * Picks a random date in {@code [lower, upper]} (inclusive) whose containing month is open for
     * {@code domain} — current live data shows every month in every open fiscal year is itself
     * open, so this resolves on the first or second attempt in practice; still genuinely checks
     * rather than assuming, for correctness. Falls back to {@code lower} if the range is
     * empty/inverted (a narrow edge case, e.g. a fiscal year that only just started) or no open
     * date is found within a bounded number of attempts, rather than looping forever or throwing.
     */
    private static LocalDate randomOpenDate(Jdbi jdbi, AdmYear year, long ouCode, PeriodDomain domain,
            LocalDate lower, LocalDate upper) {
        if (upper.isBefore(lower)) return lower;
        long spanDays = ChronoUnit.DAYS.between(lower, upper);
        int maxAttempts = (int) Math.min(spanDays + 1, 60);
        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            LocalDate candidate = lower.plusDays(ThreadLocalRandom.current().nextLong(spanDays + 1));
            if (isMonthOpen(jdbi, candidate, year.getYcode(), ouCode, domain)) {
                return candidate;
            }
        }
        return lower; // last-resort fallback — every attempt landed in a closed month
    }
}
