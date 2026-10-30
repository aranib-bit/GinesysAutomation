package com.tests.API;

import com.gsl.api.client.VendorApiClient;
import com.gsl.api.webapi.model.request.party.TdsException;
import com.gsl.api.webapi.model.request.party.VendorMasterCreateRequest;
import com.gsl.utils.Faker.party.Completeness;
import com.gsl.utils.Faker.party.IndianIdentifiers;
import com.gsl.utils.Faker.party.IndianPartyGenerator;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Map;

/**
 * The {@code VendorMaster/Create} request the framework builds: only the name/id, PAN, environment codes and the three TDS fields vary; the
 * rest is the POJO default (proven equal to the HAR bodies in {@link PartyCreateWireTest}). Update-body tests cover the extinct transform.
 */
public class VendorCreateBodyUnitTest {

    private static final java.util.List<com.gsl.api.common.CityRef> CITIES = java.util.List.of(new com.gsl.api.common.CityRef("KOLKATA", "WEST BENGAL", "INDIA"));

    private static final VendorApiClient.EnvCodes ENV = new VendorApiClient.EnvCodes(2, 26, 1, 1, 4, 2, 1);

    private static VendorMasterCreateRequest build(VendorApiClient.VendorSpec spec) {
        return VendorApiClient.buildCreateRequest(spec, ENV, 1, List.of(), new IndianPartyGenerator(new java.util.Random(5)).next(Completeness.TYPICAL), CITIES);
    }

    @Test(description = "No-TDS vendor: tdsapp N, no source, no natures")
    public void noTdsBody() {
        VendorMasterCreateRequest b = build(new VendorApiClient.VendorSpec("N1", "ZT1", false, List.of()));
        Assert.assertEquals(b.getTdsapp(), "N");
        Assert.assertNull(b.getTdssrccode());
        Assert.assertEquals(b.getFinTDSExceptions().size(), 0);
        Assert.assertEquals(b.getSlname(), "N1");
        Assert.assertEquals(b.getSlid(), "ZT1");
    }

    @Test(description = "TDS applicable, no nature (HAR CreateVendorTDSApplicable)")
    public void plainTdsBody() {
        VendorMasterCreateRequest b = build(new VendorApiClient.VendorSpec("T1", "ZT2", true, List.of()));
        Assert.assertEquals(b.getTdsapp(), "Y");
        Assert.assertEquals(b.getTdssrccode().longValue(), 1L);
        Assert.assertEquals(b.getFinTDSExceptions().size(), 0);
    }

    @Test(description = "TDS with one plain nature (HAR ...withsinglenature) and an exception nature (HAR ...withexpectionnature)")
    public void natureBodies() {
        VendorApiClient.NatureSpec plain = VendorApiClient.NatureSpec.plain(4, "TDS ON COMMISSION(678)", 1, "Hena Embroidery");
        VendorApiClient.NatureSpec exception = new VendorApiClient.NatureSpec(4, "TDS ON COMMISSION(678)", 1, "Hena Embroidery",
                "crt6778", "2026-04-01T00:00:00", "2027-03-31T00:00:00", 0, 50000);
        TdsException e1 = build(new VendorApiClient.VendorSpec("S1", "ZT3", true, List.of(plain))).getFinTDSExceptions().get(0);
        Assert.assertEquals(e1.getTdsTypeCode(), 4L);
        Assert.assertEquals(e1.getCertificateNo(), "");
        Assert.assertEquals(e1.getRate(), "");

        TdsException e2 = build(new VendorApiClient.VendorSpec("E1", "ZT4", true, List.of(exception))).getFinTDSExceptions().get(0);
        Assert.assertEquals(e2.getCertificateNo(), "crt6778");
        Assert.assertEquals(((Number) e2.getMaxLimit()).intValue(), 50000);
        Assert.assertEquals(e2.getDateFrom(), "2026-04-01T00:00:00");
    }

    @Test(description = "Environment codes come from the lookups; PAN is generated and well-formed; UDF list is carried")
    public void envCodesAndPan() {
        VendorMasterCreateRequest b = build(new VendorApiClient.VendorSpec("P1", "ZT5", false, List.of()));
        Assert.assertEquals(b.getClscode().intValue(), 2);
        Assert.assertEquals(b.getGlcode().intValue(), 26);
        Assert.assertEquals(b.getAdmcmptaxcode().intValue(), 4);
        Assert.assertTrue(IndianIdentifiers.isValidPan(b.getPan()), "PAN format: " + b.getPan());
        Assert.assertEquals(b.getGstinTaxPayerType(), "", "no GSTIN is generated for vendors");
        Assert.assertNotNull(b.getUdfDetails(), "udfDetails must be present (possibly empty)");
        Assert.assertEquals(b.getCmpcode(), Long.valueOf(1L), "the company code is mandatory on the server");
    }

    @Test(description = "VendorMaster/Update body built from a GetVendor record: ext set, read-only keys dropped, UDFs folded (HAR ExtinctVendor)")
    @SuppressWarnings("unchecked")
    public void extinctUpdateBody() {
        Map<String, Object> got = new java.util.LinkedHashMap<>();
        got.put("code", 193);
        got.put("slname", "V");
        got.put("ext", "N");
        got.put("bdist", null);
        got.put("purchaseglcode", null);
        got.put("glName", "Sundry Creditors");
        got.put("arapLedger", Map.of("x", 1));
        got.put("udfstring01", "abc");
        got.put("udfstring21", null);
        got.put("udfnum01", 5);
        got.put("udfdate01", "2025-07-04T00:00:00");
        got.put("finTDSExceptions", List.of(Map.of("tdsTypeCode", 3, "tdsTypeName", "TDS ON TECHNICAL SERVICE", "admOUCode", 1, "admOUName", "OU")));

        Map<String, Object> b = VendorApiClient.buildUpdateBody(got, true);
        Assert.assertEquals(b.get("ext"), "Y");
        Assert.assertEquals(b.get("slname"), "V");
        Assert.assertFalse(b.containsKey("arapLedger"), "read-only object dropped");
        Assert.assertFalse(b.containsKey("glName"), "read-only glName dropped");
        Assert.assertFalse(b.containsKey("udfstring01"), "flat UDF columns folded away");
        Assert.assertEquals(b.get("glname"), "");
        Assert.assertEquals(b.get("purchaseglcode"), 0);
        List<Map<String, Object>> udfs = (List<Map<String, Object>>) b.get("udfDetails");
        Assert.assertEquals(udfs.size(), 55, "40 string + 10 numeric + 5 date UDFs");
        Assert.assertEquals(udfs.get(0).get("value"), "abc");
        Assert.assertEquals(udfs.get(0).get("datatype"), "editablelist");
        Assert.assertEquals(udfs.get(20).get("value"), "", "null string UDF sent as empty");
        Assert.assertEquals(udfs.get(50).get("value"), "04/07/2025", "date UDF sent as dd/MM/yyyy");
        Map<String, Object> n = ((List<Map<String, Object>>) b.get("finTDSExceptions")).get(0);
        Assert.assertEquals(((Number) n.get("code")).intValue(), 0);
        Assert.assertEquals(n.get("certificateno"), "");
        Assert.assertEquals(VendorApiClient.buildUpdateBody(got, false).get("ext"), "N");
    }
}
