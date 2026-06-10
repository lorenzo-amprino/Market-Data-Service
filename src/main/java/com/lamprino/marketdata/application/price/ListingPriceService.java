package com.lamprino.marketdata.application.price;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.lamprino.marketdata.domain.model.LatestListingPrice;
import com.lamprino.marketdata.domain.repository.ListingPriceRepository;

@Service
public class ListingPriceService {

    private final ListingPriceRepository repository;

    public ListingPriceService(ListingPriceRepository repository) {
        this.repository = repository;
    }

    public Optional<LatestListingPrice> latestPrice(UUID listingId, String status) {
        return repository.findLatestByListingId(listingId, finalOnly(status));
    }

    private boolean finalOnly(String status) {
        if (status == null || status.isBlank()) {
            return false;
        }
        if ("final".equals(status.trim())) {
            return true;
        }
        throw new InvalidLatestPriceRequestException();
    }

    public static class InvalidLatestPriceRequestException extends RuntimeException {
    }
}
