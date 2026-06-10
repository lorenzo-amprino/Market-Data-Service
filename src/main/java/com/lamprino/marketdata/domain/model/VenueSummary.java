package com.lamprino.marketdata.domain.model;

public record VenueSummary(
        String venueCode,
        String name,
        String country,
        String timezone,
        String calendarCode) {
}
