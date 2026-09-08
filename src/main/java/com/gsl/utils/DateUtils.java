package com.gsl.utils;

import java.time.format.DateTimeFormatter;

/**
 * Single source of truth for the date format the application's own UI date inputs expect
 * (e.g. Document Date, Instrument/Cheque Date). Introduced after a confirmed live bug where
 * a test-data builder formatted one such date via {@code LocalDate.toString()} (ISO-8601,
 * {@code yyyy-MM-dd}) while the header's own Document Date field used {@code dd/MM/yyyy} —
 * the app rejected the mismatched field outright. See the "Round 22" GV plan entry for
 * FIN-GV-ADD-COMBO-05.
 */
public final class DateUtils {

    public static final DateTimeFormatter UI_DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private DateUtils() {}
}
