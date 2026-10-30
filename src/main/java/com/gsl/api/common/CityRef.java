package com.gsl.api.common;

/** One row of the city master ({@code Shared/GetCityLookup}). A party's {@code bctname} is a foreign key to it, so a city must come from this list. */
public record CityRef(String name, String state, String country) {}
