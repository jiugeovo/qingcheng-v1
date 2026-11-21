package com.example.ordering.anniversary;

import java.time.LocalDate;

/** Gregorian anniversaries; February 29 falls on February 28 in non-leap years. */
final class AnniversaryDates {

    private AnniversaryDates() {
    }

    static LocalDate nextOccurrence(LocalDate date, boolean repeatYearly, LocalDate today) {
        if (!date.isBefore(today)) {
            return date;
        }
        if (!repeatYearly) {
            return null;
        }
        LocalDate next = date.withYear(today.getYear());
        if (!next.isBefore(today)) {
            return next;
        }
        // Stay within MySQL DATE's representable range, even at the upper boundary.
        return today.getYear() >= 9999 ? null : date.withYear(today.getYear() + 1);
    }
}
