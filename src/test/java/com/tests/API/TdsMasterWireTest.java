package com.tests.API;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gsl.api.core.QueryParams;
import com.gsl.api.webapi.factory.TdsMasterRequestFactory;
import com.gsl.api.webapi.model.response.tds.TdsAgencyListResponse;
import com.gsl.api.webapi.model.response.tds.TdsSectionListResponse;
import com.gsl.api.webapi.model.response.tds.TdsSourceListResponse;
import com.gsl.api.webapi.model.response.tds.TdsUdfConfig;
import com.tests.API.support.WireAssert;
import org.testng.annotations.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

/** The TDS master request POJOs must serialise to the bodies the UI recorded (HAR: TDScreate, Mixed), and the list responses must read into the typed items. */
public class TdsMasterWireTest {

    private static final long USER = 513150;

    @Test
    public void sourceBody() {
        WireAssert.sameJson(TdsMasterRequestFactory.source("tds source 1", "C", USER), "tds/source-create-request.json");
    }

    @Test
    public void accountClassBodyIsTheRecordedClassRecordWithCodeZero() {
        WireAssert.sameJson(TdsMasterRequestFactory.agencyClass("TDS"), "tds/account-class-create-request.json");
    }

    @Test
    public void listBodyUsesTheCapitalisedOuKey() {
        WireAssert.sameJson(TdsMasterRequestFactory.list(1, 1, USER, "2026-10-28T00:00:00", "tdsSource ASC"), "tds/source-list-request.json");
    }

    @Test
    public void sectionBodyCarriesTheRatePerSource() {
        WireAssert.sameJson(TdsMasterRequestFactory.section(USER, "tds name 1", "tds section 1", 350, "Goods", 4, "tds source 1", "2026-10-01T00:00:00",
                BigDecimal.valueOf(3)), "tds/section-create-request.json");
    }

    @Test
    public void agencyBodySendsEveryConfiguredUserDefinedField() throws IOException {
        // Rebuild the configuration the recorded request was made under from its own udfValues, then expect the same request back.
        JsonNode recorded = new ObjectMapper().readTree(WireAssert.sample("tds/agency-create-request.json")).get("udfValues");
        List<TdsUdfConfig> configs = new ArrayList<>();
        for (JsonNode udf : recorded) {
            TdsUdfConfig config = new TdsUdfConfig();
            config.setColumnName(udf.get("fieldName").asText().toUpperCase());
            config.setDataType(udf.get("datatype").asText());
            config.setMandatory(false);
            configs.add(config);
        }
        var request = TdsMasterRequestFactory.agency("ag1", "ag1", 11, 29, USER, TdsMasterRequestFactory.udfValues(configs));
        // Everything but the user-defined values (the recorded request had a few typed in) must equal the recording ...
        WireAssert.sameJson(request, "tds/agency-create-request.json", java.util.Set.of("udfValues"));
        // ... and every configured field is sent, in order, with its datatype and no value.
        assertEquals(request.getUdfValues().size(), recorded.size());
        for (int i = 0; i < recorded.size(); i++) {
            assertEquals(request.getUdfValues().get(i).getFieldName(), recorded.get(i).get("fieldName").asText());
            assertEquals(request.getUdfValues().get(i).getDatatype(), recorded.get(i).get("datatype").asText());
            assertEquals(request.getUdfValues().get(i).getValue(), null);
        }
    }

    @Test
    public void aMandatoryUdfIsValuedFromItsDefaultOnly() {
        TdsUdfConfig mandatory = new TdsUdfConfig();
        mandatory.setColumnName("UDFSTRING01");
        mandatory.setDataType("String");
        mandatory.setMandatory(true);
        mandatory.setDefaultValue("X");
        TdsUdfConfig optional = new TdsUdfConfig();
        optional.setColumnName("UDFSTRING02");
        optional.setDataType("String");
        optional.setDefaultValue("ignored");
        var values = TdsMasterRequestFactory.udfValues(List.of(mandatory, optional));
        assertEquals(values.get(0).getValue(), "X");
        assertEquals(values.get(1).getValue(), null);
        assertEquals(values.get(0).getFieldName(), "udfstring01");
    }

    @Test
    public void agencyLookupQueryCarriesTheTdsAgencyModuleAndBothOuKeys() {
        Map<String, Object> query = QueryParams.toMap(TdsMasterRequestFactory.agencyLookup(1, 1, USER, "2026-10-28T00:00:00", List.of("className", "classTypeDisplay")));
        assertEquals(query.get("module"), "tdsagency");
        assertTrue(query.containsKey("connectedOUCode") && query.containsKey("connectedSiteOUCode") && query.containsKey("connectedUserCode"), query.keySet().toString());
        assertEquals(query.get("connectedSiteType"), "OS-OO-CM");
    }

    @Test
    public void theListResponsesReadIntoTypedItems() {
        var sources = WireAssert.read("tds/source-list-response.json", TdsSourceListResponse.class).items();
        assertEquals(sources.get(0).getName(), "COMPANY");
        assertEquals(sources.get(0).getCode(), 1L);
        var sections = WireAssert.read("tds/section-list-response.json", TdsSectionListResponse.class).items();
        assertEquals(sections.get(0).getType(), "Services");
        assertEquals(sections.get(0).getExtinct(), "No");
        var agencies = WireAssert.read("tds/agency-list-response.json", TdsAgencyListResponse.class).items();
        assertEquals(agencies.get(0).getAgencyId(), "00784");
        assertEquals(agencies.get(0).getClassType(), "D");
    }
}
