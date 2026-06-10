package com.lamprino.marketdata.api.price;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.lamprino.marketdata.application.price.ListingPriceService;
import com.lamprino.marketdata.domain.model.LatestListingPrice;

@RestController
class ListingPriceController {

    private final ListingPriceService service;

    ListingPriceController(ListingPriceService service) {
        this.service = service;
    }

    @GetMapping("/listings/{listingId}/prices/latest")
    LatestListingPriceResponse latest(
            @PathVariable UUID listingId,
            @RequestParam(value = "status", required = false) String status) {
        return service.latestPrice(listingId, status)
                .map(LatestListingPriceResponse::from)
                .orElseThrow(ListingNotFoundException::new);
    }

    record LatestListingPriceResponse(
            @JsonProperty("listing_id") UUID listingId,
            @JsonProperty("instrument_id") UUID instrumentId,
            LocalDate date,
            BigDecimal price,
            @JsonProperty("price_kind") String priceKind,
            BigDecimal open,
            BigDecimal high,
            BigDecimal low,
            BigDecimal close,
            @JsonProperty("adjusted_close") BigDecimal adjustedClose,
            BigDecimal volume,
            String currency,
            String status,
            @JsonProperty("data_availability") String dataAvailability,
            @JsonProperty("data_availability_reason") String dataAvailabilityReason,
            @JsonProperty("data_provider") String dataProvider,
            @JsonProperty("retrieved_at") OffsetDateTime retrievedAt) {

        static LatestListingPriceResponse from(LatestListingPrice price) {
            return new LatestListingPriceResponse(
                    price.listingId(),
                    price.instrumentId(),
                    price.date(),
                    price.price(),
                    price.hasObservedPrice() ? "close" : null,
                    price.open(),
                    price.high(),
                    price.low(),
                    price.close(),
                    price.adjustedClose(),
                    price.volume(),
                    price.currency(),
                    price.status(),
                    price.dataAvailability(),
                    price.dataAvailabilityReason(),
                    price.dataProvider(),
                    price.retrievedAt());
        }
    }

    @ResponseStatus(HttpStatus.NOT_FOUND)
    static class ListingNotFoundException extends RuntimeException {
    }
}
