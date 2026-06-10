package com.lamprino.marketdata.domain.repository;

import java.util.Optional;
import java.util.UUID;

import com.lamprino.marketdata.domain.model.LatestListingPrice;

public interface ListingPriceRepository {

    Optional<LatestListingPrice> findLatestByListingId(UUID listingId, boolean finalOnly);
}
