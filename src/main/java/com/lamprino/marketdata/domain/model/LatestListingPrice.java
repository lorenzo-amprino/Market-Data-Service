package com.lamprino.marketdata.domain.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record LatestListingPrice(
        UUID listingId,
        UUID instrumentId,
        LocalDate date,
        BigDecimal open,
        BigDecimal high,
        BigDecimal low,
        BigDecimal close,
        BigDecimal adjustedClose,
        BigDecimal volume,
        String currency,
        String status,
        String dataAvailability,
        String dataAvailabilityReason,
        String dataProvider,
        OffsetDateTime retrievedAt) {

    public BigDecimal price() {
        return close;
    }

    public boolean hasObservedPrice() {
        return date != null;
    }
}
