package com.gsl.utils.Faker.party;

import java.util.Random;

/** Alternates Indian and foreign parties at random. */
public final class MixedPartyGenerator implements PartyDataGenerator {

    private final PartyDataGenerator indian;
    private final PartyDataGenerator foreign;
    private final Random random;

    public MixedPartyGenerator(Random random, PartyDataGenerator indian, PartyDataGenerator foreign) {
        this.random = random;
        this.indian = indian;
        this.foreign = foreign;
    }

    @Override
    public PartyProfile next(Completeness level) {
        return (random.nextBoolean() ? indian : foreign).next(level);
    }
}
