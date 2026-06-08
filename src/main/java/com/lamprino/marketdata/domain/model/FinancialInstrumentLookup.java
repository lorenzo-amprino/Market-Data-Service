package com.lamprino.marketdata.domain.model;

public record FinancialInstrumentLookup(
        String matchedBy,
        FinancialInstrumentSummary instrument,
        ListingSummary listing) {
}
