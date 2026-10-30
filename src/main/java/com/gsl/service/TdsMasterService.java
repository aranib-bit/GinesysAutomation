package com.gsl.service;

import com.gsl.api.client.MasterDataSupport;
import com.gsl.api.client.TdsMasterApiClient;
import com.gsl.api.client.TdsMasterApiClient.Context;
import com.gsl.api.model.common.SubLedgerLookupResult;
import com.gsl.api.model.generalvoucher.GlLedgerLookupResult;
import com.gsl.api.webapi.factory.TdsMasterRequestFactory;
import com.gsl.api.webapi.model.request.tds.AgencyUdfValue;
import com.gsl.api.webapi.model.response.tds.TdsAgencyItem;
import com.gsl.api.webapi.model.response.tds.TdsSectionItem;
import com.gsl.api.webapi.model.response.tds.TdsSourceItem;
import com.gsl.api.webapi.model.response.tds.TdsUdfConfig;
import com.gsl.config.ApiConfig;
import com.gsl.config.ConfigFactory;
import com.gsl.api.core.ApiException;
import com.gsl.reports.ReportLogger;
import org.testng.SkipException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

import static com.gsl.api.webapi.constants.TdsMasterDefaults.*;

/**
 * Creates only what a TDS-applicable vendor needs and the environment lacks, in dependency order: a TDS source, an agency (class and ledger from
 * the agency form's own lookups), and TDS sections (natures) carrying a rate for the source the vendor will use. Existing masters are reused and never
 * edited; every master created is a new throw-away record named with {@link com.gsl.api.webapi.constants.TdsMasterDefaults#NAME_PREFIX}. Values that
 * depend on the environment (rate, rate validity, which ledger) come from {@link ApiConfig}.
 */
public class TdsMasterService {

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final TdsMasterApiClient client;
    private final Context context;

    public TdsMasterService(TdsMasterApiClient client, Context context) {
        this.client = client;
        this.context = context;
    }

    /** Makes sure at least one TDS source exists, creating a corporate one when none does. */
    public void ensureSource() {
        if (!client.listSources(context).isEmpty()) return;
        String name = NAME_PREFIX + " SRC " + stamp();
        ReportLogger.dataBackfill("[TdsMasterService] No TDS source exists - creating '{}'.", name);
        client.createSource(context, name);
        awaitListed("TDS source '" + name + "'", () -> client.listSources(context).stream().anyMatch(s -> name.equals(s.getName())));
    }

    /**
     * Makes sure at least {@code wanted} TDS sections exist (and, when {@code preferredType} is {@code 'S'} or {@code 'G'}, one of that type), each with
     * a rate for {@code sourceCode}. Creates the missing ones against an existing or new agency.
     *
     * @return how many sections were created
     * @throws SkipException when the environment lacks what a master needs (no agency class, no matching ledger, a mandatory user-defined field)
     */
    public int ensureSections(long sourceCode, int wanted, String preferredType) {
        String preferred = typeOf(preferredType);
        List<TdsSectionItem> active = client.listSections(context).stream().filter(s -> LIST_NOT_EXTINCT.equalsIgnoreCase(s.getExtinct())).toList();
        boolean needPreferred = preferred != null && active.stream().noneMatch(s -> preferred.equalsIgnoreCase(s.getType()));
        int missing = Math.max(Math.max(0, wanted - active.size()), needPreferred ? 1 : 0);
        if (missing == 0) return 0;

        String sourceName = client.listSources(context).stream().filter(s -> s.getCode() == sourceCode).map(TdsSourceItem::getName).findFirst()
                .orElseThrow(() -> new SkipException("Cannot create TDS sections: source " + sourceCode + " is not in the TDS source list."));
        long agencySlCode = ensureAgency();
        String stamp = stamp();
        List<String> created = new ArrayList<>();
        for (int i = 0; i < missing; i++) {
            String type = i == 0 && needPreferred ? preferred : alternate(preferred, i);
            String name = NAME_PREFIX + " NAME " + stamp + "-" + i;
            ReportLogger.dataBackfill("[TdsMasterService] Creating TDS section '{}' ({}, rate {} from {}).", name, type, rate(), rateValidFrom());
            client.createSection(TdsMasterRequestFactory.section(context.empCode(), name, "ZZ-" + stamp + "-" + i, agencySlCode, type, sourceCode, sourceName,
                    rateValidFrom(), rate()));
            created.add(name);
        }
        for (String name : created) {
            awaitListed("TDS section '" + name + "'", () -> client.listSections(context).stream().anyMatch(s -> name.equals(s.getTdsName())));
        }
        return created.size();
    }

    /**
     * {@code count} new section bodies for the k6 bulk hand-over: makes sure a source and an agency exist first (the only masters a section depends
     * on), then builds the bodies with the same rate, validity and naming as {@link #ensureSections}; nothing is posted here.
     *
     * @param type {@code 'S'} Services, {@code 'G'} Goods, or null to alternate
     */
    public List<com.gsl.api.webapi.model.request.tds.TdsSectionCreateRequest> buildSectionRequests(int count, String runId, String type) {
        ensureSource();
        TdsSourceItem source = client.listSources(context).stream().findFirst()
                .orElseThrow(() -> new SkipException("Cannot create TDS sections: the TDS source list is empty."));
        long agencySlCode = ensureAgency();
        String preferred = typeOf(type);
        List<com.gsl.api.webapi.model.request.tds.TdsSectionCreateRequest> out = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            out.add(TdsMasterRequestFactory.section(context.empCode(), sectionNamePrefix(runId) + i, "ZZ-" + runId + "-" + i, agencySlCode,
                    alternate(preferred, i), source.getCode(), source.getName(), rateValidFrom(), rate()));
        }
        return out;
    }

    /** {@code count} new corporate source bodies for the k6 bulk hand-over; nothing is posted here. */
    public List<com.gsl.api.webapi.model.request.tds.TdsSourceCreateRequest> buildSourceRequests(int count, String runId) {
        List<com.gsl.api.webapi.model.request.tds.TdsSourceCreateRequest> out = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            out.add(TdsMasterRequestFactory.source(sourceNamePrefix(runId) + i, SOURCE_TYPE_CORPORATE, context.empCode()));
        }
        return out;
    }

    /** What every section name of a run starts with; lets the run be found again in the section list. */
    public static String sectionNamePrefix(String runId) {
        return NAME_PREFIX + " NAME " + runId + " ";
    }

    /** What every source name of a run starts with. */
    public static String sourceNamePrefix(String runId) {
        return NAME_PREFIX + " SRC " + runId + " ";
    }

    /** The code (sub-ledger code) of an active agency: an existing one, else a new agency in the TDS class and ledger the agency form offers. */
    public long ensureAgency() {
        List<TdsAgencyItem> agencies = client.listAgencies(context);
        TdsAgencyItem existing = agencies.stream().filter(a -> LIST_NOT_EXTINCT.equalsIgnoreCase(a.getExtinct())).findFirst().orElse(null);
        if (existing != null) return existing.getCode();

        SubLedgerLookupResult agencyClass = ensureAgencyClass();
        String ledgerText = ConfigFactory.getApiConfig().backfillTdsAgencyLedgerNameContains().toLowerCase(Locale.ROOT);
        List<GlLedgerLookupResult> offeredLedgers = client.fetchAgencyLedgers(context);
        // Prefer the ledger whose name carries the configured text (a TDS payable ledger); an agency may also be booked to another liability
        // ledger the screen offers (existing agencies are), so fall back to the first of those rather than demand a particular master.
        GlLedgerLookupResult ledger = offeredLedgers.stream()
                .filter(l -> l.getGlName() != null && l.getGlName().toLowerCase(Locale.ROOT).contains(ledgerText)).findFirst()
                .or(() -> offeredLedgers.stream().filter(l -> LEDGER_TYPE_LIABILITY.equalsIgnoreCase(l.getType())).findFirst())
                .orElseThrow(() -> new SkipException("Cannot create a TDS agency: the TDS Agency screen offers no liability ledger ("
                        + offeredLedgers.stream().map(GlLedgerLookupResult::getGlName).toList() + ")."));
        List<AgencyUdfValue> udf = agencyUdfValues();

        String agencyId = "ZA" + (System.currentTimeMillis() % 1_000_000L);
        String name = NAME_PREFIX + " AGENCY " + stamp();
        ReportLogger.dataBackfill("[TdsMasterService] No TDS agency exists - creating '{}' (id {}, class {}, ledger '{}').", name, agencyId, agencyClass.getClassName(), ledger.getGlName());
        client.createAgency(context, agencyId, name, agencyClass.getClassCode(), ledger.getGlCode(), udf);
        return awaitListed("TDS agency '" + name + "'", () -> client.listAgencies(context).stream().filter(a -> agencyId.equals(a.getAgencyId()))
                .map(TdsAgencyItem::getCode).findFirst().orElse(null));
    }

    /** The TDS Agency class the agency form offers: an existing one, else a new class of that type (waited for until the form offers it). */
    private SubLedgerLookupResult ensureAgencyClass() {
        SubLedgerLookupResult existing = client.fetchAgencyClass(context);
        if (existing != null) return existing;
        String name = NAME_PREFIX + " CLASS " + stamp();
        ReportLogger.dataBackfill("[TdsMasterService] No TDS Agency account class exists - creating '{}'.", name);
        client.createAgencyClass(name);
        return awaitListed("TDS Agency class '" + name + "'", () -> client.fetchAgencyClass(context));
    }

    /** Every configured user-defined field, empty unless the configuration makes it mandatory (then its default is used; none means a data skip). */
    private List<AgencyUdfValue> agencyUdfValues() {
        List<TdsUdfConfig> configs = client.fetchAgencyUdfConfigs();
        for (TdsUdfConfig c : configs) {
            if (c.isRequired() && (c.getDefaultValue() == null || c.getDefaultValue().isBlank())) {
                throw new SkipException("Cannot create a TDS agency: user-defined field " + c.getColumnName() + " is mandatory and has no default value.");
            }
        }
        return TdsMasterRequestFactory.udfValues(configs);
    }

    private <T> T awaitListed(String what, Supplier<T> found) {
        T value = MasterDataSupport.pollFor(VISIBLE_TIMEOUT_MS, VISIBLE_POLL_MS, () -> {
            T v = found.get();
            return Boolean.FALSE.equals(v) ? null : v;
        });
        if (value == null) throw new ApiException(what + " was created but did not appear in its list within " + VISIBLE_TIMEOUT_MS / 1000 + "s.", 0);
        return value;
    }

    private static String typeOf(String letter) {
        if (letter == null) return null;
        return switch (letter.toUpperCase(Locale.ROOT).charAt(0)) {
            case 'S' -> SECTION_TYPE_SERVICES;
            case 'G' -> SECTION_TYPE_GOODS;
            default -> null;
        };
    }

    /** The type of the {@code index}-th section when no type is demanded: alternate, starting with Services, so a mixed set exists. */
    private static String alternate(String preferred, int index) {
        if (preferred != null) return preferred;
        return index % 2 == 0 ? SECTION_TYPE_SERVICES : SECTION_TYPE_GOODS;
    }

    private BigDecimal rate() {
        return BigDecimal.valueOf(ConfigFactory.getApiConfig().backfillTdsSectionRate());
    }

    private String rateValidFrom() {
        String configured = ConfigFactory.getApiConfig().backfillTdsSectionRateValidFrom();
        LocalDate from = configured == null || configured.isBlank() ? LocalDate.now().withDayOfMonth(1) : LocalDate.parse(configured.trim());
        return from + "T00:00:00";
    }

    private static String stamp() {
        return LocalDateTime.now().format(STAMP);
    }
}
