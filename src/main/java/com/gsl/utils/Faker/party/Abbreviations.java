package com.gsl.utils.Faker.party;

/** Short upper-case codes derived from a name ("Kariwala Textiles" -> "KARIWALA"). */
final class Abbreviations {

    private Abbreviations() {}

    static String of(String name) {
        String first = name.trim().split("[\\s,.-]+")[0].replaceAll("[^A-Za-z0-9]", "").toUpperCase();
        return first.isEmpty() ? "PARTY" : first;
    }
}
