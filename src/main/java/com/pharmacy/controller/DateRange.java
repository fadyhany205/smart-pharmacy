package com.pharmacy.controller;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/** Date filter used by list and report pages. Defaults to the current month. */
public record DateRange(LocalDate from, LocalDate to) {
    public static DateRange of(LocalDate from, LocalDate to) {
        LocalDate today = LocalDate.now();
        LocalDate f = from == null ? today.withDayOfMonth(1) : from;
        LocalDate t = to == null ? today : to;
        if (t.isBefore(f)) t = f;
        return new DateRange(f, t);
    }

    public LocalDateTime start() { return from.atStartOfDay(); }

    public LocalDateTime end() { return to.atTime(LocalTime.MAX); }
}
