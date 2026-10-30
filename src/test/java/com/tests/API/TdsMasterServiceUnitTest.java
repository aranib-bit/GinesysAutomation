package com.tests.API;

import com.gsl.api.client.TdsMasterApiClient;
import com.gsl.api.client.TdsMasterApiClient.Context;
import com.gsl.api.model.common.SubLedgerLookupResult;
import com.gsl.api.model.generalvoucher.GlLedgerLookupResult;
import com.gsl.api.webapi.model.request.tds.AgencyUdfValue;
import com.gsl.api.webapi.model.request.tds.TdsSectionCreateRequest;
import com.gsl.api.webapi.model.response.tds.TdsAgencyItem;
import com.gsl.api.webapi.model.response.tds.TdsSectionItem;
import com.gsl.api.webapi.model.response.tds.TdsSourceItem;
import com.gsl.api.webapi.model.response.tds.TdsUdfConfig;
import com.gsl.service.TdsMasterService;
import com.tests.API.support.StubApiServer;
import org.testng.SkipException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

/** The find-or-create chain of the TDS masters against an in-memory client: reuse what exists, create only what is missing, skip clearly when the environment cannot. */
public class TdsMasterServiceUnitTest {

    private static final Context CONTEXT = new Context(1, 1, 513150, "2026-10-29T00:00:00");

    private StubApiServer server;
    private FakeTds tds;

    /** An in-memory TDS master: lists start from the test's setup, creates add to them. */
    private static final class FakeTds extends TdsMasterApiClient {
        final List<TdsSourceItem> sources = new ArrayList<>();
        final List<TdsAgencyItem> agencies = new ArrayList<>();
        final List<TdsSectionItem> sections = new ArrayList<>();
        final List<TdsSectionCreateRequest> sectionRequests = new ArrayList<>();
        SubLedgerLookupResult agencyClass = SubLedgerLookupResult.builder().classCode(11).className("TDS").classType("D").build();
        List<GlLedgerLookupResult> ledgers = List.of(GlLedgerLookupResult.builder().glCode(29).glName("TDS Payable Through Agency").build());
        List<TdsUdfConfig> udf = List.of();
        int classCreates;
        int agencyCreates;
        long agencyLedger;
        int sourceCreates;

        FakeTds(StubApiServer server) {
            super(server.spec());
        }

        @Override public List<TdsSourceItem> listSources(Context c) { return sources; }

        @Override public void createSource(Context c, String name) {
            sourceCreates++;
            TdsSourceItem s = new TdsSourceItem();
            s.setCode(sources.size() + 1L);
            s.setName(name);
            sources.add(s);
        }

        @Override public List<TdsAgencyItem> listAgencies(Context c) { return agencies; }

        @Override public void createAgency(Context c, String agencyId, String name, long classCode, long glCode, List<AgencyUdfValue> udfValues) {
            agencyCreates++;
            agencyLedger = glCode;
            TdsAgencyItem a = new TdsAgencyItem();
            a.setCode(350);
            a.setAgencyId(agencyId);
            a.setName(name);
            a.setExtinct("No");
            agencies.add(a);
        }

        @Override public List<TdsSectionItem> listSections(Context c) { return sections; }

        @Override public void createSection(TdsSectionCreateRequest request) {
            sectionRequests.add(request);
            TdsSectionItem s = new TdsSectionItem();
            s.setCode(sections.size() + 1L);
            s.setTdsName(request.getTdsName());
            s.setType(request.getSectionType());
            s.setExtinct("No");
            sections.add(s);
        }

        @Override public void createAgencyClass(String name) {
            classCreates++;
            agencyClass = SubLedgerLookupResult.builder().classCode(12).className(name).classType("D").build();
        }

        @Override public SubLedgerLookupResult fetchAgencyClass(Context c) { return agencyClass; }

        @Override public List<GlLedgerLookupResult> fetchAgencyLedgers(Context c) { return ledgers; }

        @Override public List<TdsUdfConfig> fetchAgencyUdfConfigs() { return udf; }
    }

    @BeforeClass
    public void start() {
        server = StubApiServer.start();
    }

    @AfterClass(alwaysRun = true)
    public void stop() {
        server.close();
    }

    @BeforeMethod
    public void reset() {
        tds = new FakeTds(server);
        TdsSourceItem source = new TdsSourceItem();
        source.setCode(1);
        source.setName("COMPANY");
        tds.sources.add(source);
    }

    private TdsMasterService service() {
        return new TdsMasterService(tds, CONTEXT);
    }

    private static TdsSectionItem section(String name, String type) {
        TdsSectionItem s = new TdsSectionItem();
        s.setTdsName(name);
        s.setType(type);
        s.setExtinct("No");
        return s;
    }

    @Test
    public void bulkSectionBodiesAreBuiltWithoutPostingAnySection() {
        List<TdsSectionCreateRequest> bodies = service().buildSectionRequests(3, "r1", null);
        assertEquals(bodies.size(), 3);
        assertEquals(tds.sectionRequests.size(), 0, "building bodies must not post");
        assertEquals(tds.agencyCreates, 1, "the one master a section depends on is made when missing");
        for (int i = 0; i < 3; i++) {
            assertTrue(bodies.get(i).getTdsName().startsWith(TdsMasterService.sectionNamePrefix("r1")), bodies.get(i).getTdsName());
            assertEquals(bodies.get(i).getAgencySlCode(), 350L);
            assertEquals(bodies.get(i).getSourceDetails().get(0).getSourceCode(), 1L);
        }
        assertEquals(bodies.get(0).getSectionType(), "Services");
        assertEquals(bodies.get(1).getSectionType(), "Goods");
    }

    @Test
    public void bulkSectionAndSourceBodiesKeepTheWireNamesOfTheCapturedRequests() {
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        java.util.Map<?, ?> section = mapper.convertValue(service().buildSectionRequests(1, "r2", "S").get(0), java.util.Map.class);
        for (String key : List.of("tDSName", "tDSAgencySLCode", "tdssourceDetails", "sectionType", "ext")) {
            assertTrue(section.containsKey(key), key + " missing in " + section.keySet());
        }
        java.util.Map<?, ?> source = mapper.convertValue(service().buildSourceRequests(1, "r2").get(0), java.util.Map.class);
        assertTrue(source.containsKey("tdsSource") && source.containsKey("sourceType"), source.keySet().toString());
    }

    @Test
    public void anExistingSourceIsReusedNotDuplicated() {
        service().ensureSource();
        assertEquals(tds.sourceCreates, 0);
    }

    @Test
    public void aMissingSourceIsCreatedAsANewThrowAwayRecord() {
        tds.sources.clear();
        service().ensureSource();
        assertEquals(tds.sourceCreates, 1);
        assertTrue(tds.sources.get(0).getName().startsWith("ZZ API TDS SRC"), tds.sources.get(0).getName());
    }

    @Test
    public void enoughExistingSectionsCreateNothing() {
        tds.sections.add(section("A", "Services"));
        tds.sections.add(section("B", "Goods"));
        assertEquals(service().ensureSections(1, 2, null), 0);
        assertEquals(tds.agencyCreates, 0);
    }

    @Test
    public void missingSectionsAreCreatedAgainstANewAgencyWithTheRateForTheSource() {
        assertEquals(service().ensureSections(1, 2, null), 2);
        assertEquals(tds.agencyCreates, 1);
        assertEquals(tds.sectionRequests.size(), 2);
        TdsSectionCreateRequest first = tds.sectionRequests.get(0);
        assertEquals(first.getAgencySlCode(), 350L);
        assertEquals(first.getSourceDetails().get(0).getSourceCode(), 1L);
        assertEquals(first.getSourceDetails().get(0).getSourceName(), "COMPANY");
        assertTrue(first.getTdsName().startsWith("ZZ API TDS NAME"), first.getTdsName());
        // No demanded type: the created sections alternate so a mixed set exists.
        assertEquals(first.getSectionType(), "Services");
        assertEquals(tds.sectionRequests.get(1).getSectionType(), "Goods");
    }

    @Test
    public void anExistingAgencyIsReused() {
        TdsAgencyItem agency = new TdsAgencyItem();
        agency.setCode(316);
        agency.setAgencyId("00784");
        agency.setExtinct("No");
        tds.agencies.add(agency);
        service().ensureSections(1, 1, null);
        assertEquals(tds.agencyCreates, 0);
        assertEquals(tds.sectionRequests.get(0).getAgencySlCode(), 316L);
    }

    @Test
    public void aDemandedTypeIsCreatedEvenWhenEnoughSectionsExist() {
        tds.sections.add(section("A", "Services"));
        tds.sections.add(section("B", "Services"));
        assertEquals(service().ensureSections(1, 2, "G"), 1);
        assertEquals(tds.sectionRequests.get(0).getSectionType(), "Goods");
    }

    @Test
    public void withoutATdsNamedLedgerTheFirstLiabilityLedgerIsUsed() {
        tds.ledgers = List.of(GlLedgerLookupResult.builder().glCode(14).glName("Sundry Debtors").type("A").build(),
                GlLedgerLookupResult.builder().glCode(24).glName("Sundry Creditors for Expenses").type("L").build());
        assertEquals(service().ensureSections(1, 1, null), 1);
        assertEquals(tds.agencyCreates, 1);
        assertEquals(tds.agencyLedger, 24L);
    }

    @Test
    public void noLiabilityLedgerAtAllIsACleanDataSkip() {
        tds.ledgers = List.of(GlLedgerLookupResult.builder().glCode(14).glName("Sundry Debtors").type("A").build());
        SkipException skip = expectThrows(SkipException.class, () -> service().ensureSections(1, 1, null));
        assertTrue(skip.getMessage().contains("liability ledger"), skip.getMessage());
        assertEquals(tds.agencyCreates, 0);
    }

    @Test
    public void aMissingAgencyClassIsCreatedAsANewThrowAwayClassBeforeTheAgency() {
        tds.agencyClass = null;
        assertEquals(service().ensureSections(1, 1, null), 1);
        assertEquals(tds.classCreates, 1);
        assertEquals(tds.agencyCreates, 1);
        assertTrue(tds.agencyClass.getClassName().startsWith("ZZ API TDS CLASS"), tds.agencyClass.getClassName());
    }

    @Test
    public void anExistingAgencyClassIsReused() {
        service().ensureSections(1, 1, null);
        assertEquals(tds.classCreates, 0);
    }

    @Test
    public void aMandatoryUdfWithoutADefaultIsACleanDataSkip() {
        TdsUdfConfig mandatory = new TdsUdfConfig();
        mandatory.setColumnName("UDFSTRING01");
        mandatory.setMandatory(true);
        tds.udf = List.of(mandatory);
        SkipException skip = expectThrows(SkipException.class, () -> service().ensureSections(1, 1, null));
        assertTrue(skip.getMessage().contains("UDFSTRING01"), skip.getMessage());
    }

    @Test
    public void aSourceThatIsNotListedIsACleanDataSkip() {
        expectThrows(SkipException.class, () -> service().ensureSections(99, 1, null));
    }
}
