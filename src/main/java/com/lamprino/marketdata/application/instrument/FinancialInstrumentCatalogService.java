package com.lamprino.marketdata.application.instrument;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.lamprino.marketdata.domain.model.FinancialInstrumentDetail;
import com.lamprino.marketdata.domain.model.FinancialInstrumentLookup;
import com.lamprino.marketdata.domain.model.FinancialInstrumentSummary;
import com.lamprino.marketdata.domain.model.ListingDetail;
import com.lamprino.marketdata.domain.repository.FinancialInstrumentCatalogRepository;

@Service
public class FinancialInstrumentCatalogService {

    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 100;

    private final FinancialInstrumentCatalogRepository repository;

    public FinancialInstrumentCatalogService(FinancialInstrumentCatalogRepository repository) {
        this.repository = repository;
    }

    public List<FinancialInstrumentSummary> search(String query, Integer limit) {
        String normalizedQuery = query == null ? "" : query.trim();
        if (normalizedQuery.isBlank()) {
            return List.of();
        }
        return repository.search(normalizedQuery, normalizeLimit(limit));
    }

    public Optional<FinancialInstrumentDetail> findById(UUID instrumentId) {
        return repository.findById(instrumentId);
    }

    public Optional<List<ListingDetail>> listingsForInstrument(UUID instrumentId) {
        if (!repository.existsById(instrumentId)) {
            return Optional.empty();
        }
        return Optional.of(repository.findListingsByInstrumentId(instrumentId));
    }

    public Optional<FinancialInstrumentLookup> lookupByIsin(String isin) {
        String normalizedIsin = normalizeText(isin);
        if (normalizedIsin.isBlank()) {
            return Optional.empty();
        }
        return repository.findByIsin(normalizedIsin);
    }

    public Optional<FinancialInstrumentLookup> lookupByVenueCodeAndSymbol(String venueCode, String symbol) {
        String normalizedVenueCode = normalizeText(venueCode);
        String normalizedSymbol = normalizeText(symbol);
        if (normalizedVenueCode.isBlank() || normalizedSymbol.isBlank()) {
            return Optional.empty();
        }
        return repository.findByVenueCodeAndSymbol(normalizedVenueCode, normalizedSymbol);
    }

    public Optional<FinancialInstrumentLookup> lookupByProviderIdentifier(String providerCode, String providerIdentifier) {
        String normalizedProviderCode = normalizeText(providerCode);
        String normalizedProviderIdentifier = normalizeText(providerIdentifier);
        if (normalizedProviderCode.isBlank() || normalizedProviderIdentifier.isBlank()) {
            return Optional.empty();
        }
        return repository.findByProviderIdentifier(normalizedProviderCode, normalizedProviderIdentifier);
    }

    private String normalizeText(String value) {
        return value == null ? "" : value.trim();
    }

    private int normalizeLimit(Integer requestedLimit) {
        if (requestedLimit == null) {
            return DEFAULT_LIMIT;
        }
        if (requestedLimit < 1) {
            return DEFAULT_LIMIT;
        }
        return Math.min(requestedLimit, MAX_LIMIT);
    }
}
