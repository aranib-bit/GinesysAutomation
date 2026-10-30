package com.gsl.utils.Faker.party;

import com.gsl.utils.Faker.FakerContext;

/**
 * Hands every thread its own {@link PartyDataGenerator} (Faker is not thread-safe and tests run with {@code parallel="methods"}), built on the
 * thread's shared {@link FakerContext} (region, locales and seed come from {@code FakerConfig}).
 */
public final class PartyDataFactory {

    private static final ThreadLocal<PartyDataGenerator> PER_THREAD =
            ThreadLocal.withInitial(() -> create(FakerContext.current()));

    private PartyDataFactory() {}

    /** The calling thread's generator. */
    public static PartyDataGenerator generator() {
        return PER_THREAD.get();
    }

    /** A generator for the region of {@code context}, sharing its random stream. */
    public static PartyDataGenerator create(FakerContext context) {
        return create(context.region(), context.foreignLocaleTags(), context.random());
    }

    /** A generator for {@code region}, drawing from {@code random} (also used directly by tests). */
    public static PartyDataGenerator create(Region region, java.util.List<String> foreignLocales, java.util.Random random) {
        return switch (region) {
            case INDIA -> new IndianPartyGenerator(random);
            case FOREIGN -> new ForeignPartyGenerator(random, foreignLocales);
            case MIXED -> new MixedPartyGenerator(random, new IndianPartyGenerator(random), new ForeignPartyGenerator(random, foreignLocales));
        };
    }

    /** A fresh generator with its own seeded context (tests). */
    public static PartyDataGenerator create(Region region, java.util.List<String> foreignLocales, Long seed) {
        return create(FakerContext.create(region, foreignLocales, seed));
    }
}
