package com.tests.API;

import com.gsl.api.webapi.factory.DocSchemeRequestFactory;
import com.tests.API.support.WireAssert;
import org.testng.annotations.Test;

import java.util.List;

/** The numbering-scheme admin POJOs must send exactly what the hand-built Map bodies sent before the migration (golden files in {@code har-samples/docscheme/}). */
public class DocSchemeWireTest {

    private static final String WORK_DATE = "2026-10-29T00:00:00";

    @Test
    public void listBody() {
        WireAssert.sameJson(DocSchemeRequestFactory.list(WORK_DATE), "docscheme/list-request.json");
    }

    @Test
    public void untaggedBody() {
        WireAssert.sameJson(DocSchemeRequestFactory.untagged(WORK_DATE), "docscheme/untagged-request.json");
    }

    @Test
    public void createBodyCarriesEveryKeyIncludingTheNulls() {
        WireAssert.sameJson(DocSchemeRequestFactory.create("ZZ Test", "ZT", 5, "api backfill", "26-27", "2026-04-01T00:00:00", "2027-03-31T00:00:00"),
                "docscheme/create-request.json");
    }

    @Test
    public void tagBodyHasOneEntryPerMenu() {
        WireAssert.sameJson(DocSchemeRequestFactory.tag(214, List.of("FinanceAPVoucher", "FinanceARVoucher")), "docscheme/tag-request.json");
    }
}
