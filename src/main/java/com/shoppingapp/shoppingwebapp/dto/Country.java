package com.shoppingapp.shoppingwebapp.dto;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * One entry in the country select.
 *
 * <p>Built from the JDK's own ISO 3166 data rather than a hand-kept list, so it
 * cannot drift out of date and nobody maintains 250 lines of constants.
 */
public record Country(String code, String name) {

    private static final List<Country> ALL = Arrays.stream(Locale.getISOCountries())
            .map(code -> new Country(code, Locale.of("", code).getDisplayCountry(Locale.ENGLISH)))
            .filter(country -> !country.name().isBlank())
            .sorted(Comparator.comparing(Country::name))
            .toList();

    public static List<Country> all() {
        return ALL;
    }
}
