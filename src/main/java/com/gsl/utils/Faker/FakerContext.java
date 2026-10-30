package com.gsl.utils.Faker;

import com.github.javafaker.Faker;
import com.gsl.config.ConfigFactory;
import com.gsl.config.FakerConfig;
import com.gsl.utils.Faker.party.Region;
import com.gsl.utils.Log;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;

/**
 * The one source of randomness and locale for every generator ({@code FakerUtils}, party, document text, item, payment references): a
 * {@link Random}, the configured {@link Region} and one Faker per locale. Faker is not thread-safe and tests run with
 * {@code parallel="methods"}, so every thread has its own context. With {@code testdata.faker.seed} set, the n-th thread that asks gets seed
 * {@code seed + n}, so a single-threaded run is exactly reproducible; the seed is always logged.
 */
public final class FakerContext {

    private static final AtomicLong THREAD_SEQUENCE = new AtomicLong();
    private static final ThreadLocal<FakerContext> CURRENT = ThreadLocal.withInitial(FakerContext::fromConfig);

    private final Region region;
    private final List<Locale> foreignLocales;
    private final Random random;
    private final Map<Locale, Faker> fakers = new HashMap<>();

    private FakerContext(Region region, List<String> foreignLocaleTags, Random random) {
        this.region = region;
        this.random = random;
        List<Locale> locales = new ArrayList<>();
        for (String tag : foreignLocaleTags) locales.add(Locale.forLanguageTag(tag.trim()));
        this.foreignLocales = locales;
    }

    /** The calling thread's context. */
    public static FakerContext current() {
        return CURRENT.get();
    }

    /** A fresh context from {@link FakerConfig}. */
    public static FakerContext fromConfig() {
        FakerConfig cfg = ConfigFactory.getFakerConfig();
        return create(cfg.region(), Arrays.stream(cfg.foreignLocales().split(",")).map(String::trim).filter(t -> !t.isEmpty()).toList(), cfg.seed());
    }

    /** A fresh context; {@code seed} null means a random seed (logged). */
    public static FakerContext create(Region region, List<String> foreignLocaleTags, Long seed) {
        long effective = seed != null ? seed + THREAD_SEQUENCE.getAndIncrement() : new Random().nextLong();
        Log.info("[Faker] region={} foreignLocales={} seed={}", region, foreignLocaleTags, effective);
        return new FakerContext(region, foreignLocaleTags, new Random(effective));
    }

    /**
     * A context whose seed derives from {@code scenarioId} (plus the configured seed, if any): a given scenario always gets the same data,
     * whatever thread or order it runs in. For fixture factories that define coverage by scenario (enum + predicate).
     */
    public static FakerContext forScenario(String scenarioId) {
        FakerConfig cfg = ConfigFactory.getFakerConfig();
        long seed = (cfg.seed() == null ? 0L : cfg.seed()) * 1_000_003L + scenarioId.hashCode();
        return forScenario(scenarioId, cfg.region(), Arrays.stream(cfg.foreignLocales().split(",")).map(String::trim).filter(t -> !t.isEmpty()).toList(), seed);
    }

    /** {@link #forScenario(String)} with explicit region, locales and base seed (tests). */
    public static FakerContext forScenario(String scenarioId, Region region, List<String> foreignLocaleTags, long seed) {
        long effective = seed * 31L + scenarioId.hashCode();
        Log.info("[Faker] scenario={} region={} seed={}", scenarioId, region, effective);
        return new FakerContext(region, foreignLocaleTags, new Random(effective));
    }

    public Region region() {
        return region;
    }

    public Random random() {
        return random;
    }

    public List<String> foreignLocaleTags() {
        return foreignLocales.stream().map(Locale::toLanguageTag).toList();
    }

    /** A Faker for {@code locale}, sharing this context's {@link Random}. */
    public Faker faker(Locale locale) {
        return fakers.computeIfAbsent(locale, l -> new Faker(l, random));
    }

    /** The Indian Faker (en-IN). */
    public Faker indianFaker() {
        return faker(new Locale("en", "IN"));
    }

    /** A Faker for the configured region: en-IN, a foreign locale, or one of the two at random (MIXED). */
    public Faker faker() {
        boolean indian = switch (region) {
            case INDIA -> true;
            case FOREIGN -> false;
            case MIXED -> random.nextBoolean();
        };
        if (indian || foreignLocales.isEmpty()) return indianFaker();
        return faker(foreignLocales.get(random.nextInt(foreignLocales.size())));
    }
}
