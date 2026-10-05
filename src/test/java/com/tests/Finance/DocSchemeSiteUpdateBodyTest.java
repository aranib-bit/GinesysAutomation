package com.tests.Finance;

import com.gsl.api.client.DocSchemeAdminApiClient;
import com.gsl.api.model.udf.UdfFieldMetadata;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

/**
 * {@link DocSchemeAdminApiClient#buildSiteUpdateBody} — pure rules for the whole-site re-post (shape taken
 * from the "NumberingSchemeTaggingToOU" HAR). Run with {@code -Dtest=DocSchemeSiteUpdateBodyTest}.
 */
public class DocSchemeSiteUpdateBodyTest {

    private static Map<String, Object> existingEntry(long code, String name, long docCode) {
        Map<String, Object> ds = new LinkedHashMap<>();
        ds.put("docName", name); ds.put("ext", "N"); ds.put("sampleNumber", "X/00001"); ds.put("isExcise", "N");
        ds.put("gstAppl", "N"); ds.put("batchAppl", "N"); ds.put("code", docCode);
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("admOU", Map.of("name", "OU"));          // GET-only, must be dropped
        entry.put("documentScheme", ds);
        entry.put("dataVersion", 5);                       // GET-only, must be dropped
        entry.put("code", code);
        return entry;
    }

    private static UdfFieldMetadata meta(String column, String datatype) {
        return UdfFieldMetadata.builder().columnName(column).datatype(datatype).build();
    }

    private static Map<String, Object> site() {
        Map<String, Object> site = new LinkedHashMap<>();
        site.put("name", "Hena Embroidery");
        site.put("code", 1);
        site.put("fax", null);                 // UI turns this into ""
        site.put("mobile", "9830094766");      // untouched
        site.put("udfstring01", "abc");
        site.put("udfnum01", 2134567.0);       // GET gives a double, UI posts an integer
        site.put("udfdate01", null);
        List<Object> assigned = new ArrayList<>();
        assigned.add(existingEntry(3781, "Old Scheme", 211));
        site.put("assignDocSchemes", assigned);
        return site;
    }

    private static Map<String, Object> newScheme() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("docName", "AR AP Test"); m.put("ext", "N"); m.put("sampleNumber", "TAR/001/26-27");
        m.put("isExcise", "N"); m.put("gstAppl", "N"); m.put("batchAppl", "N"); m.put("code", 215);
        m.put("somethingElse", "ignored");
        return m;
    }

    private static final List<UdfFieldMetadata> META = List.of(
            meta("UDFSTRING01", "EditableList"), meta("UDFNUM01", "Numeric"), meta("UDFDATE01", "Date"));

    @Test
    @SuppressWarnings("unchecked")
    public void appendsNewSchemeAndKeepsExistingOnesInUiShape() {
        Map<String, Object> body = DocSchemeAdminApiClient.buildSiteUpdateBody(site(), META, newScheme());
        List<Map<String, Object>> assigned = (List<Map<String, Object>>) body.get("assignDocSchemes");
        assertEquals(assigned.size(), 2);
        // existing: only {code, documentScheme} — GET-only admOU/dataVersion dropped
        assertEquals(assigned.get(0).keySet().toString(), "[code, documentScheme]");
        assertEquals(assigned.get(0).get("code"), 3781L);
        // new: code 0 and only the 7 documentScheme keys
        assertEquals(assigned.get(1).get("code"), 0);
        Map<String, Object> ds = (Map<String, Object>) assigned.get(1).get("documentScheme");
        assertEquals(ds.keySet().toString(), "[docName, ext, sampleNumber, isExcise, gstAppl, batchAppl, code]");
        assertEquals(ds.get("code"), 215);
    }

    @Test
    public void normalisesNullStringsAndAddsUiOnlyFieldsButLeavesOtherFieldsAlone() {
        Map<String, Object> body = DocSchemeAdminApiClient.buildSiteUpdateBody(site(), META, newScheme());
        assertEquals(body.get("fax"), "");
        assertEquals(body.get("mobile"), "9830094766");
        assertEquals(body.get("name"), "Hena Embroidery");
        assertEquals(body.get("bdist"), "");
        assertEquals(body.get("udfString07"), "");
        assertTrue(body.containsKey("salformcode"));
        assertNull(body.get("salformcode"));
        assertFalse(body.containsKey("id"), "the UI's ExtJS model id must not be invented");
    }

    @Test
    @SuppressWarnings("unchecked")
    public void rebuildsUdfDetailsFromValuesAndMetadataDatatypes() {
        Map<String, Object> body = DocSchemeAdminApiClient.buildSiteUpdateBody(site(), META, newScheme());
        List<Map<String, Object>> udf = (List<Map<String, Object>>) body.get("udfDetails");
        assertEquals(udf.size(), 3);
        assertEquals(udf.get(0).toString(), "{fieldName=udfstring01, value=abc, datatype=editablelist}");
        assertEquals(udf.get(1).get("value"), 2134567L);          // whole double → integer, like the UI
        assertEquals(udf.get(1).get("datatype"), "numeric");
        assertNull(udf.get(2).get("value"));
        assertEquals(udf.get(2).get("datatype"), "date");
    }

    @Test
    public void doesNotMutateTheFetchedSiteEntity() {
        Map<String, Object> site = site();
        DocSchemeAdminApiClient.buildSiteUpdateBody(site, META, newScheme());
        assertNull(site.get("fax"));
        assertEquals(((List<?>) site.get("assignDocSchemes")).size(), 1);
        assertFalse(site.containsKey("udfDetails"));
    }
}
