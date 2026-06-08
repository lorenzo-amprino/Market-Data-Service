package com.lamprino.marketdata.domain.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.lamprino.marketdata.domain.model.FinancialInstrumentDetail;
import com.lamprino.marketdata.domain.model.FinancialInstrumentLookup;
import com.lamprino.marketdata.domain.model.FinancialInstrumentSummary;

public interface FinancialInstrumentCatalogRepository {

    List<FinancialInstrumentSummary> search(String query, int limit);

    Optional<FinancialInstrumentDetail> findById(UUID instrumentId);

    Optional<FinancialInstrumentLookup> findByIsin(String isin);

    Optional<FinancialInstrumentLookup> findByVenueCodeAndSymbol(String venueCode, String symbol);

    Optional<FinancialInstrumentLookup> findByProviderIdentifier(String providerCode, String providerIdentifier);
}
