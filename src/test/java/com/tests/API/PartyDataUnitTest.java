package com.tests.API;

import com.gsl.utils.Faker.party.Completeness;
import com.gsl.utils.Faker.party.FieldLimits;
import com.gsl.utils.Faker.party.IndianIdentifiers;
import com.gsl.utils.Faker.party.PartyDataFactory;
import com.gsl.utils.Faker.party.PartyDataGenerator;
import com.gsl.utils.Faker.party.PartyProfile;
import com.gsl.utils.Faker.party.Region;
import org.testng.annotations.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotEquals;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

/** The party data utility: identifier formats, completeness levels, regions, field limits, seeding and thread isolation. */
public class PartyDataUnitTest {

    private static final List<String> LOCALES = List.of("en-GB", "en-US", "en-AU", "en-CA");

    // ───────────── identifiers ─────────────

    @Test
    public void panIsWellFormedForEveryHolderType() {
        Random r = new Random(1);
        for (char type : "CPFHATBLJG".toCharArray()) {
            for (int i = 0; i < 50; i++) assertTrue(IndianIdentifiers.isValidPan(IndianIdentifiers.pan(r, type)), "type " + type);
        }
        expectThrows(IllegalArgumentException.class, () -> IndianIdentifiers.pan(r, 'X'));
        assertFalse(IndianIdentifiers.isValidPan("ayipb7164n"), "lower case is not a canonical PAN");
    }

    @Test
    public void gstinChecksumMatchesAKnownRealGstin() {
        // Real GSTINs: the first is the widely cited sample, the others are suppliers in the live master. Their checksum characters must verify.
        assertTrue(IndianIdentifiers.isValidGstin("27AAPFU0939F1ZV"));
        assertTrue(IndianIdentifiers.isValidGstin("19AIUPK9539N2ZZ"));
        assertTrue(IndianIdentifiers.isValidGstin("24DGOPM4991M1ZJ"));
        assertFalse(IndianIdentifiers.isValidGstin("27AAPFU0939F1ZW"), "a wrong checksum character must be rejected");
        // 07AAACG2115R1ZN (a customer in the live master) fails its own checksum: test data, not a valid GSTIN.
        assertFalse(IndianIdentifiers.isValidGstin("07AAACG2115R1ZN"));
    }

    @Test
    public void generatedGstinsAreValidAndEmbedThePan() {
        Random r = new Random(2);
        for (int i = 0; i < 200; i++) {
            String pan = IndianIdentifiers.companyPan(r);
            String state = IndianIdentifiers.randomStateCode(r);
            String gstin = IndianIdentifiers.gstin(state, pan, 1);
            assertTrue(IndianIdentifiers.isValidGstin(gstin), gstin);
            assertEquals(gstin.substring(2, 12), pan);
            assertEquals(gstin.substring(0, 2), state);
            assertEquals(gstin.length(), FieldLimits.GSTIN);
        }
    }

    @Test
    public void ifscPinMobileAndAccountHaveTheRightShape() {
        Random r = new Random(3);
        for (int i = 0; i < 200; i++) {
            assertTrue(IndianIdentifiers.ifsc(r).matches("[A-Z]{4}0[A-Z0-9]{6}"));
            assertTrue(IndianIdentifiers.pin(r).matches("[1-9][0-9]{5}"));
            assertTrue(IndianIdentifiers.mobile(r).matches("[6-9][0-9]{9}"));
            assertTrue(IndianIdentifiers.bankAccount(r).matches("[1-9][0-9]{10}"));
        }
    }

    // ───────────── generators ─────────────

    private static PartyDataGenerator generator(Region region, long seed) {
        return PartyDataFactory.create(region, LOCALES, seed);
    }

    @Test
    public void minimalCarriesOnlyTheCoreFields() {
        PartyProfile p = generator(Region.INDIA, 10).next(Completeness.MINIMAL);
        assertFalse(p.name().isBlank());
        assertFalse(p.contactPerson().isBlank());
        assertTrue(p.mobile().matches("[6-9][0-9]{9}"));
        assertTrue(IndianIdentifiers.isValidPan(p.pan()));
        for (String empty : new String[]{p.address(), p.city(), p.pin(), p.email(), p.website(), p.gstin(), p.bankName(), p.bankAccount(), p.ifsc()}) {
            assertEquals(empty, "");
        }
    }

    @Test
    public void typicalAddsAddressAndEmailButNoGstinOrBank() {
        PartyProfile p = generator(Region.INDIA, 11).next(Completeness.TYPICAL);
        assertFalse(p.address().isBlank());
        assertFalse(p.city().isBlank());
        assertTrue(p.pin().matches("[1-9][0-9]{5}"));
        assertTrue(p.email().contains("@"));
        assertEquals(p.website(), "");
        assertEquals(p.gstin(), "");
        assertEquals(p.bankAccount(), "");
    }

    @Test
    public void fullAddsWebsiteGstinAndBankForAnIndianParty() {
        PartyProfile p = generator(Region.INDIA, 12).next(Completeness.FULL);
        assertTrue(p.website().startsWith("www."));
        assertTrue(IndianIdentifiers.isValidGstin(p.gstin()), p.gstin());
        assertEquals(p.gstin().substring(2, 12), p.pan());
        assertEquals(p.gstStateCode(), p.gstin().substring(0, 2));
        assertTrue(p.ifsc().matches("[A-Z]{4}0[A-Z0-9]{6}"));
        assertFalse(p.bankName().isBlank());
    }

    @Test
    public void foreignPartiesNeverGetAGstinOrIfsc() {
        PartyDataGenerator g = generator(Region.FOREIGN, 13);
        for (int i = 0; i < 40; i++) {
            PartyProfile p = g.next(Completeness.FULL);
            assertEquals(p.region(), Region.FOREIGN);
            assertEquals(p.gstin(), "");
            assertEquals(p.ifsc(), "");
            assertFalse(p.name().isBlank());
            assertFalse(p.mobile().isBlank());
        }
    }

    @Test
    public void mixedProducesBothRegions() {
        PartyDataGenerator g = generator(Region.MIXED, 14);
        Set<Region> seen = new HashSet<>();
        for (int i = 0; i < 60; i++) seen.add(g.next(Completeness.TYPICAL).region());
        assertEquals(seen, Set.of(Region.INDIA, Region.FOREIGN));
    }

    @Test
    public void everyValueFitsItsColumn() {
        for (Region region : new Region[]{Region.INDIA, Region.FOREIGN}) {
            PartyDataGenerator g = generator(region, 15);
            for (int i = 0; i < 100; i++) {
                PartyProfile p = g.next(Completeness.FULL);
                assertTrue(p.name().length() <= FieldLimits.NAME);
                assertTrue(p.abbreviation().length() <= FieldLimits.ABBREVIATION);
                assertTrue(p.address().length() <= FieldLimits.ADDRESS);
                assertTrue(p.pin().length() <= FieldLimits.PIN);
                assertTrue(p.mobile().length() <= FieldLimits.PHONE);
                assertTrue(p.landline().length() <= FieldLimits.PHONE);
                assertTrue(p.email().length() <= FieldLimits.EMAIL);
                assertTrue(p.website().length() <= FieldLimits.WEBSITE);
                assertTrue(p.contactPerson().length() <= FieldLimits.CONTACT_PERSON);
            }
        }
    }

    @Test
    public void theSameSeedRepeatsTheSameParties() {
        // The factory adds a per-thread sequence number to a given seed, so reproducibility is checked on the seeded generators directly.
        PartyDataGenerator a = new com.gsl.utils.Faker.party.IndianPartyGenerator(new Random(99));
        PartyDataGenerator b = new com.gsl.utils.Faker.party.IndianPartyGenerator(new Random(99));
        for (int i = 0; i < 10; i++) assertEquals(a.next(Completeness.FULL), b.next(Completeness.FULL));
        PartyDataGenerator c = new com.gsl.utils.Faker.party.IndianPartyGenerator(new Random(100));
        assertNotEquals(new com.gsl.utils.Faker.party.IndianPartyGenerator(new Random(99)).next(Completeness.FULL), c.next(Completeness.FULL));
    }

    @Test
    public void withGstinForStateRebuildsFromThePan() {
        PartyProfile p = generator(Region.INDIA, 16).next(Completeness.TYPICAL).withGstinForState("19");
        assertTrue(IndianIdentifiers.isValidGstin(p.gstin()));
        assertEquals(p.gstStateCode(), "19");
        expectThrows(IllegalStateException.class, () -> generator(Region.FOREIGN, 17).next(Completeness.TYPICAL).withGstinForState("19"));
    }

    @Test
    public void eachThreadGetsItsOwnGenerator() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(4);
        try {
            Set<PartyDataGenerator> generators = ConcurrentHashMap.newKeySet();
            List<Future<?>> futures = new java.util.ArrayList<>();
            for (int i = 0; i < 4; i++) {
                futures.add(pool.submit(() -> {
                    generators.add(PartyDataFactory.generator());
                    for (int n = 0; n < 50; n++) assertFalse(PartyDataFactory.generator().next(Completeness.TYPICAL).name().isBlank());
                }));
            }
            for (Future<?> f : futures) f.get();
            assertTrue(generators.size() >= 2, "threads must not share one generator instance, saw " + generators.size());
        } finally {
            pool.shutdownNow();
        }
    }
}
