package com.lamprino.marketdata.domain.model;

import java.util.UUID;

public record ListingSummary(
        UUID listingId,
        String venueCode,
        String symbol,
        String currency,
        String status,
        boolean preferred) {
}
