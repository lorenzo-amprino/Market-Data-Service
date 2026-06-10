package com.lamprino.marketdata.domain.model;

import java.util.UUID;

public record ListingDetail(
        UUID listingId,
        String symbol,
        String currency,
        String status,
        boolean preferred,
        String dataAvailability,
        String dataAvailabilityReason,
        VenueSummary venue) {
}
