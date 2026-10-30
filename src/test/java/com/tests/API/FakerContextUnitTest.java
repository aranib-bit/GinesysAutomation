package com.tests.API;

import com.gsl.utils.Faker.FakerContext;
import com.gsl.utils.Faker.FakerUtils;
import com.gsl.utils.Faker.party.Completeness;
import com.gsl.utils.Faker.party.PartyDataFactory;
import com.gsl.utils.Faker.party.Region;
import org.testng.annotations.Test;

import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotEquals;
import static org.testng.Assert.assertTrue;

/** The shared faker context: scenario-seeded reproducibility (for enum/predicate fixture factories) and the FakerUtils facade on top of it. */
public class FakerContextUnitTest {

    private static final List<String> LOCALES = List.of("en-GB", "en-US");

    @Test
    public void aScenarioAlwaysGetsTheSameData() {
        String a1 = PartyDataFactory.create(FakerContext.forScenario("FIN-API-PARTY-01", Region.INDIA, LOCALES, 5L)).next(Completeness.FULL).name();
        String a2 = PartyDataFactory.create(FakerContext.forScenario("FIN-API-PARTY-01", Region.INDIA, LOCALES, 5L)).next(Completeness.FULL).name();
        String b = PartyDataFactory.create(FakerContext.forScenario("FIN-API-PARTY-02", Region.INDIA, LOCALES, 5L)).next(Completeness.FULL).name();
        assertEquals(a1, a2, "same scenario, same data");
        assertNotEquals(a1, b, "different scenarios get different data");
    }

    @Test
    public void everyGeneratorOfAContextSharesItsRandomStream() {
        FakerContext c1 = FakerContext.forScenario("S", Region.INDIA, LOCALES, 9L);
        FakerContext c2 = FakerContext.forScenario("S", Region.INDIA, LOCALES, 9L);
        assertEquals(c1.faker().name().fullName(), c2.faker().name().fullName());
        assertEquals(c1.random().nextInt(1000), c2.random().nextInt(1000));
    }

    @Test
    public void foreignContextsUseTheConfiguredLocales() {
        FakerContext c = FakerContext.forScenario("S", Region.FOREIGN, LOCALES, 1L);
        assertEquals(c.region(), Region.FOREIGN);
        assertEquals(c.foreignLocaleTags(), LOCALES);
        assertTrue(c.faker().address().city().length() > 0);
    }

    @Test
    public void theFakerUtilsFacadeStillProducesItsFormats() {
        assertTrue(FakerUtils.randomReference().matches("REF-[A-Z]{4}-\\d{4}"), FakerUtils.randomReference());
        assertTrue(FakerUtils.randomRemarks().length() > 0);
        int n = FakerUtils.randomInt(5, 9);
        assertTrue(n >= 5 && n <= 9);
        assertTrue(FakerUtils.randomEmail().contains("@"));
    }
}
