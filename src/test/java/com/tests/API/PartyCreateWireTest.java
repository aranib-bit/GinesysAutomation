package com.tests.API;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gsl.api.webapi.model.request.party.CustomerCreateRequest;
import com.gsl.api.webapi.model.request.party.SubLedgerCreateRequest;
import com.gsl.api.webapi.model.request.party.TdsException;
import com.gsl.api.webapi.model.request.party.VendorMasterCreateRequest;
import com.tests.API.support.WireAssert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.testng.Assert.assertEquals;

/**
 * The party create POJOs must reproduce the bodies captured in the HARs: built with only the caller-supplied fields (identity, class,
 * environment codes, TDS), every other key must come out of the POJO defaults exactly as the UI sent it. {@code udfDetails} is data, not
 * contract, and is excluded.
 */
public class PartyCreateWireTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Set<String> IGNORED = Set.of("udfDetails");

    private static JsonNode sample(String name) throws IOException {
        return JSON.readTree(WireAssert.sample("party/" + name + ".json"));
    }

    private static Long lng(JsonNode n, String key) {
        return n.get(key).isNull() ? null : n.get(key).asLong();
    }

    private static String str(JsonNode n, String key) {
        return n.get(key).isNull() ? null : n.get(key).asText();
    }

    private static List<TdsException> natures(JsonNode s) {
        List<TdsException> out = new ArrayList<>();
        for (JsonNode e : s.get("finTDSExceptions")) out.add(JSON.convertValue(e, TdsException.class));
        return out;
    }

    private static VendorMasterCreateRequest vendor(JsonNode s) {
        return VendorMasterCreateRequest.builder()
                .slname(str(s, "slname")).slid(str(s, "slid")).pan(str(s, "pan")).bcp(str(s, "bcp")).bfX2(str(s, "bfX2"))
                .clscode(lng(s, "clscode")).glcode(lng(s, "glcode"))
                .fintradegrpcode(lng(s, "fintradegrpcode")).purtermmaincode(lng(s, "purtermmaincode"))
                .admcmptaxcode(lng(s, "admcmptaxcode")).cpcategorycode(lng(s, "cpcategorycode")).cmpcode(lng(s, "cmpcode"))
                .tdsapp(str(s, "tdsapp")).tdssrccode(lng(s, "tdssrccode"))
                .finTDSExceptions(natures(s))
                .build();
    }

    @DataProvider(name = "vendorSamples")
    public Object[][] vendorSamples() {
        return new Object[][]{
                {"vendor-create-notds"}, {"vendor-create-tds-applicable"}, {"vendor-create-tds-single-nature"},
                {"vendor-create-tds-exception-nature"}, {"vendor-create-tds-multi-nature"}, {"jobber-create"}};
    }

    @Test(dataProvider = "vendorSamples", description = "VendorMaster/Create bodies (Supplier, Jobber, TDS variants) equal the HARs")
    public void vendorMasterBodyMatchesHar(String sampleName) throws IOException {
        JsonNode s = sample(sampleName);
        WireAssert.sameJson(vendor(s), "party/" + sampleName + ".json", IGNORED);
    }

    @Test(description = "Customer/Create body equals the HAR (customer-only keys + the two sales-side values that differ from a vendor)")
    public void customerBodyMatchesHar() throws IOException {
        JsonNode s = sample("customer-create");
        CustomerCreateRequest r = CustomerCreateRequest.builder()
                .slname(str(s, "slname")).slid(str(s, "slid")).pan(str(s, "pan")).bcp(str(s, "bcp")).bfX2(str(s, "bfX2"))
                .clscode(lng(s, "clscode")).glcode(lng(s, "glcode"))
                .fintradegrpcode(lng(s, "fintradegrpcode")).purtermmaincode(lng(s, "purtermmaincode"))
                .admcmptaxcode(lng(s, "admcmptaxcode")).cpcategorycode(lng(s, "cpcategorycode"))
                .saltradegrpcode(lng(s, "saltradegrpcode")).saltermcode(lng(s, "saltermcode")).pricelistcode(lng(s, "pricelistcode"))
                .saletype(str(s, "saletype")).salepostmethod(str(s, "salepostmethod"))
                .build();
        WireAssert.sameJson(r, "party/customer-create.json", IGNORED);
    }

    @Test(description = "Vendor and customer share exactly the 127 base keys; the subclasses add 7 and 6")
    public void keySetsAreTheDocumentedOnes() throws IOException {
        Set<String> base = new java.util.TreeSet<>();
        JSON.valueToTree(VendorMasterCreateRequest.builder().build()).fieldNames().forEachRemaining(base::add);
        Set<String> customer = new java.util.TreeSet<>();
        JSON.valueToTree(CustomerCreateRequest.builder().build()).fieldNames().forEachRemaining(customer::add);
        assertEquals(base.size(), 134);
        assertEquals(customer.size(), 133);
        Set<String> shared = new java.util.TreeSet<>(base);
        shared.retainAll(customer);
        assertEquals(shared.size(), 127);
        long annotated = java.util.Arrays.stream(SubLedgerCreateRequest.class.getDeclaredFields())
                .filter(f -> f.isAnnotationPresent(com.fasterxml.jackson.annotation.JsonProperty.class)).count();
        assertEquals(annotated, 127L);
    }
}
