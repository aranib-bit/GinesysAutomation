package com.gsl.utils.Faker.party;

import java.text.Normalizer;
import java.util.Locale;

/** E-mail and website addresses derived from names, ASCII only. */
final class Emails {

    private Emails() {}

    private static String slug(String text) {
        String ascii = Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        String s = ascii.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", ".").replaceAll("^\\.+|\\.+$", "");
        return s.isEmpty() ? "party" : s;
    }

    /** {@code contact.name@company.example.com}. */
    static String of(String contact, String company) {
        return slug(contact) + "@" + slug(company).replace('.', '-') + ".example.com";
    }

    /** {@code www.company.example.com}. */
    static String website(String company) {
        return "www." + slug(company).replace('.', '-') + ".example.com";
    }
}
