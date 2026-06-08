package com.lamprino.marketdata.domain.model;

import java.util.List;
import java.util.UUID;

public record FinancialInstrumentSummary(
        UUID instrumentId,
        String name,
        String instrumentType,
        String isin,
        String status,
        String dataAvailability,
        List<ListingSummary> listings) {
}
