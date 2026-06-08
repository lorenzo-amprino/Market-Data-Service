package com.lamprino.marketdata.domain.model;

import java.util.Map;
import java.util.UUID;

public record FinancialInstrumentDetail(
        UUID instrumentId,
        String name,
        String instrumentType,
        String isin,
        String issuer,
        String issuerCountry,
        String status,
        String dataAvailability,
        String dataAvailabilityReason,
        Map<String, Object> metadata) {
}
