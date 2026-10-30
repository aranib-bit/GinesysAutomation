package com.gsl.utils.Faker.party;

/** Produces {@link PartyProfile}s. Implementations are not thread-safe; {@link PartyDataFactory} hands every thread its own. */
public interface PartyDataGenerator {

    /** The next generated party with the optional fields the {@code level} asks for. */
    PartyProfile next(Completeness level);
}
