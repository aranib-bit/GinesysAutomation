package com.gsl.utils.dataprovider.source.finance.common;

import com.gsl.config.ConfigFactory;
import com.gsl.database.dao.adm.AdmDocSchemeDao;
import com.gsl.model.adm.AdmDocSchemeCandidate;
import com.gsl.model.adm.AdmYear;
import org.jdbi.v3.core.Jdbi;
import org.testng.SkipException;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Picks a voucher document scheme that can actually still generate a document number.
 *
 * <p>A scheme is "exhausted" once its counter reaches {@code 10^width - 1}; saving a voucher under
 * it then fails. Selecting "the first scheme mapped to the menu" (the old AR/AP behaviour) or the
 * app default (GV) is therefore not safe. This resolver reads the counter
 * ({@code admlastdoc.lastno}) next to the scheme definition ({@code admdocscheme.width}) and ranks
 * the candidates itself — see {@link DocSchemeMode} for the three selection modes.
 *
 * <p>Layering: this is domain logic (it knows the doc-scheme tables and the voucher-menu mapping),
 * so it lives with the finance data sources, not in a generic {@code com.gsl.utils} package. Its
 * signatures take only primitives/collections — callers pass
 * {@code VoucherDomain#getMenuShortcode()}. {@link #select} is pure (no DB) so the ranking rules can
 * be unit-tested with hand-built candidates.
 */
public final class DocSchemeResolver {

    private DocSchemeResolver() {}

    /**
     * Resolves today's accounting year/OU from {@code ownerSiteCode}, reads the candidates and
     * selects one per {@code mode}, using the configured headroom and test-scheme names.
     *
     * @throws IllegalStateException AUTO/MANUAL: no usable scheme; or no open year/OU for today
     * @throws SkipException         EXHAUSTED: no full scheme exists to exercise
     */
    public static DocSchemeSelection resolve(Jdbi jdbi, String menuShortcode, List<Long> siteCodes,
                                             long ownerSiteCode, DocSchemeMode mode) {
        if (siteCodes == null || siteCodes.isEmpty()) {
            throw new IllegalStateException("No site codes available — cannot resolve a doc scheme for "
                    + menuShortcode);
        }
        long[] period = resolvePeriod(jdbi, ownerSiteCode);
        return resolve(jdbi, menuShortcode, siteCodes, period[0], period[1], mode,
                ConfigFactory.getFinanceConfig().docSchemeMinHeadroom(),
                testSchemeNames());
    }

    /**
     * Looks up ONE scheme by docname with its derived capacity ({@code remaining} etc.), regardless of
     * mode or test-scheme status — for callers that must inspect a specific scheme (e.g. the filler
     * checking how many numbers a dedicated test scheme still has). Empty when the scheme is not mapped
     * to {@code menuShortcode} AND enabled for {@code siteCodes}.
     */
    public static Optional<AdmDocSchemeCandidate> find(Jdbi jdbi, String menuShortcode, List<Long> siteCodes,
                                                       long ownerSiteCode, String docName) {
        long[] period = resolvePeriod(jdbi, ownerSiteCode);
        Set<String> testNames = testSchemeNames();
        return jdbi.onDemand(AdmDocSchemeDao.class)
                .findCandidates(period[1], period[0], menuShortcode, siteCodes).stream()
                .filter(c -> c.getDocname() != null && c.getDocname().equalsIgnoreCase(docName))
                .peek(c -> derive(c, testNames))
                .findFirst();
    }

    /** {@code [ouCode, ycode]} for today at {@code ownerSiteCode}: its OU and the open accounting year containing today. */
    private static long[] resolvePeriod(Jdbi jdbi, long ownerSiteCode) {
        long ouCode = AccountingPeriodResolver.resolveOuCode(jdbi, ownerSiteCode);
        LocalDate today = LocalDate.now();
        AdmYear year = AccountingPeriodResolver.findCurrentYear(jdbi, today, ouCode)
                .orElseThrow(() -> new IllegalStateException("No open accounting year contains " + today
                        + " for admou_code=" + ouCode + " (owner site " + ownerSiteCode
                        + ") — cannot check doc scheme counters."));
        return new long[] {ouCode, year.getYcode()};
    }

    /** Explicit-inputs overload (no config/period lookups) — reads candidates, then {@link #select}. */
    public static DocSchemeSelection resolve(Jdbi jdbi, String menuShortcode, List<Long> siteCodes,
                                             long ouCode, long ycode, DocSchemeMode mode,
                                             int minHeadroom, Set<String> testSchemeNames) {
        List<AdmDocSchemeCandidate> rows = jdbi.onDemand(AdmDocSchemeDao.class)
                .findCandidates(ycode, ouCode, menuShortcode, siteCodes);
        return select(rows, menuShortcode, ycode, ouCode, mode, minHeadroom, testSchemeNames);
    }

    /**
     * Pure ranking. Derives capacity/remaining on every row, then:
     * <ul>
     *   <li>{@code AUTO}: non-test, non-manual, non-extinct, {@code remaining >= minHeadroom} (or untracked
     *       interval); most remaining first (untracked last), ties → lowest doccode.</li>
     *   <li>{@code MANUAL}: non-extinct manual schemes; lowest doccode.</li>
     *   <li>{@code EXHAUSTED}: non-extinct, non-manual schemes with {@code remaining < minHeadroom};
     *       test schemes first, then lowest doccode.</li>
     * </ul>
     */
    public static DocSchemeSelection select(List<AdmDocSchemeCandidate> rows, String menuShortcode, long ycode,
                                     long ouCode, DocSchemeMode mode, int minHeadroom,
                                     Set<String> testSchemeNames) {
        List<AdmDocSchemeCandidate> candidates = new ArrayList<>(rows);
        for (AdmDocSchemeCandidate c : candidates) {
            derive(c, testSchemeNames);
        }

        List<AdmDocSchemeCandidate> pool = candidates.stream()
                .filter(c -> !c.isExtinct())
                .filter(c -> inPool(c, mode, minHeadroom))
                .sorted(comparatorFor(mode))
                .collect(Collectors.toList());

        if (pool.isEmpty()) {
            String detail = "No " + mode + " doc scheme for menu '" + menuShortcode + "' (ycode=" + ycode
                    + ", admou_code=" + ouCode + ", minHeadroom=" + minHeadroom + "). Candidates:\n"
                    + describeCandidates(candidates, null);
            if (mode == DocSchemeMode.EXHAUSTED) {
                throw new SkipException("Skipped — " + detail
                        + "\nNo full scheme exists to exercise the exhausted-scheme scenario; provision and "
                        + "fill a dedicated test scheme first.");
            }
            throw new IllegalStateException(detail
                    + "\nCheck admmenu_docscheme (mdmenushortcode='" + menuShortcode
                    + "'), admou_docscheme for the user's sites, and admlastdoc counters.");
        }

        AdmDocSchemeCandidate chosen = pool.get(0);
        List<String> narrative = new ArrayList<>();
        narrative.add("  Doc scheme (" + mode + ") for '" + menuShortcode + "': '" + chosen.getDocname()
                + "' (doccode=" + chosen.getDoccode() + ", " + remainingText(chosen) + ")");
        narrative.add("  Doc scheme candidates (ycode=" + ycode + ", admou_code=" + ouCode
                + ", minHeadroom=" + minHeadroom + "):");
        narrative.addAll(Arrays.asList(describeCandidates(candidates, chosen).split("\n")));
        return new DocSchemeSelection(chosen, mode, menuShortcode, ycode, ouCode,
                List.copyOf(candidates), List.copyOf(narrative));
    }

    // ── internals ─────────────────────────────────────────────────────────────

    /** Fills capacity / lastUsed / remaining / testScheme on {@code c}. */
    private static void derive(AdmDocSchemeCandidate c, Set<String> testSchemeNames) {
        c.setTestScheme(c.getDocname() != null && testSchemeNames.contains(normalize(c.getDocname())));
        Long width = c.getWidth();
        c.setCapacity(width == null ? null : (long) (Math.pow(10, width) - 1));

        String interval = c.getGenerationInterval() == null ? "" : c.getGenerationInterval().trim();
        long startingMinusOne = (c.getStarting() == null ? 1 : c.getStarting()) - 1;
        Long used;
        if ("Y".equalsIgnoreCase(interval)) {
            used = c.getLastnoYear() != null ? c.getLastnoYear() : startingMinusOne;   // no row yet = fresh counter
        } else if ("N".equalsIgnoreCase(interval)) {
            used = c.getLastnoAny() != null ? c.getLastnoAny() : startingMinusOne;
        } else {
            used = null;   // monthly/other reset intervals: not tracked here
        }
        c.setLastUsed(used);
        c.setRemaining(used == null || c.getCapacity() == null ? null : c.getCapacity() - used);
    }

    private static boolean inPool(AdmDocSchemeCandidate c, DocSchemeMode mode, int minHeadroom) {
        switch (mode) {
            case AUTO:
                return !c.isManualScheme() && !c.isTestScheme()
                        && (c.getRemaining() == null || c.getRemaining() >= minHeadroom);
            case MANUAL:
                return c.isManualScheme();
            case EXHAUSTED:
                return !c.isManualScheme() && c.getRemaining() != null && c.getRemaining() < minHeadroom;
            default:
                throw new IllegalArgumentException("Unhandled mode " + mode);
        }
    }

    private static Comparator<AdmDocSchemeCandidate> comparatorFor(DocSchemeMode mode) {
        Comparator<AdmDocSchemeCandidate> byDoccode = Comparator.comparing(AdmDocSchemeCandidate::getDoccode);
        switch (mode) {
            case AUTO:
                // most remaining first; untracked (null) last
                return Comparator.comparing(AdmDocSchemeCandidate::getRemaining,
                                Comparator.nullsLast(Comparator.<Long>reverseOrder()))
                        .thenComparing(byDoccode);
            case EXHAUSTED:
                // test schemes first, then lowest doccode
                return Comparator.comparing((AdmDocSchemeCandidate c) -> !c.isTestScheme()).thenComparing(byDoccode);
            default:
                return byDoccode;
        }
    }

    private static String describeCandidates(List<AdmDocSchemeCandidate> candidates, AdmDocSchemeCandidate chosen) {
        if (candidates.isEmpty()) {
            return "    (none — no scheme is mapped to this menu AND enabled for the user's sites)";
        }
        return candidates.stream()
                .map(c -> "    " + (c == chosen ? "→ " : "  ") + "'" + c.getDocname() + "' (doccode=" + c.getDoccode()
                        + ", width=" + c.getWidth() + ", " + remainingText(c)
                        + ", interval=" + c.getGenerationInterval()
                        + (c.isManualScheme() ? ", MANUAL" : "")
                        + (c.isTestScheme() ? ", TEST" : "")
                        + (c.isExtinct() ? ", EXTINCT" : "") + ")")
                .collect(Collectors.joining("\n"));
    }

    private static String remainingText(AdmDocSchemeCandidate c) {
        return c.getRemaining() == null
                ? "capacity not tracked (interval '" + c.getGenerationInterval() + "')"
                : "lastno=" + c.getLastUsed() + ", remaining=" + c.getRemaining();
    }

    /** Lower-cased, trimmed docnames from {@code docscheme.test.names} (blank entries dropped). */
    static Set<String> testSchemeNames() {
        return Arrays.stream(ConfigFactory.getFinanceConfig().docSchemeTestNames())
                .map(DocSchemeResolver::normalize)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }

    private static String normalize(String s) {
        return s == null ? "" : s.trim().toLowerCase(Locale.ROOT);
    }
}
