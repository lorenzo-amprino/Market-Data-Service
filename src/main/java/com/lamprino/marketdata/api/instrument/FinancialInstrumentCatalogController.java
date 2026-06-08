package com.lamprino.marketdata.api.instrument;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.lamprino.marketdata.application.instrument.FinancialInstrumentCatalogService;
import com.lamprino.marketdata.domain.model.FinancialInstrumentDetail;
import com.lamprino.marketdata.domain.model.FinancialInstrumentSummary;
import com.lamprino.marketdata.domain.model.ListingSummary;

@RestController
class FinancialInstrumentCatalogController {

    private final FinancialInstrumentCatalogService service;

    FinancialInstrumentCatalogController(FinancialInstrumentCatalogService service) {
        this.service = service;
    }

    @GetMapping("/instruments/search")
    InstrumentSearchResponse search(
            @RequestParam("q") String query,
            @RequestParam(value = "limit", required = false) Integer limit) {
        List<InstrumentSummaryResponse> items = service.search(query, limit).stream()
                .map(InstrumentSummaryResponse::from)
                .toList();
        return new InstrumentSearchResponse(items, new PageResponse(limit == null ? 50 : Math.min(Math.max(limit, 1), 100), null));
    }

    @GetMapping("/instruments/{instrumentId}")
    InstrumentDetailResponse detail(@PathVariable UUID instrumentId) {
        return service.findById(instrumentId)
                .map(InstrumentDetailResponse::from)
                .orElseThrow(InstrumentNotFoundException::new);
    }

    record InstrumentSearchResponse(
            List<InstrumentSummaryResponse> items,
            PageResponse page) {
    }

    record PageResponse(
            int limit,
            @JsonProperty("next_cursor") String nextCursor) {
    }

    record InstrumentSummaryResponse(
            @JsonProperty("instrument_id") UUID instrumentId,
            String name,
            @JsonProperty("instrument_type") String instrumentType,
            String isin,
            String status,
            @JsonProperty("data_availability") String dataAvailability,
            List<ListingSummaryResponse> listings) {

        static InstrumentSummaryResponse from(FinancialInstrumentSummary instrument) {
            return new InstrumentSummaryResponse(
                    instrument.instrumentId(),
                    instrument.name(),
                    instrument.instrumentType(),
                    instrument.isin(),
                    instrument.status(),
                    instrument.dataAvailability(),
                    instrument.listings().stream().map(ListingSummaryResponse::from).toList());
        }
    }

    record ListingSummaryResponse(
            @JsonProperty("listing_id") UUID listingId,
            @JsonProperty("venue_code") String venueCode,
            String symbol,
            String currency,
            String status,
            boolean preferred) {

        static ListingSummaryResponse from(ListingSummary listing) {
            return new ListingSummaryResponse(
                    listing.listingId(),
                    listing.venueCode(),
                    listing.symbol(),
                    listing.currency(),
                    listing.status(),
                    listing.preferred());
        }
    }

    record InstrumentDetailResponse(
            @JsonProperty("instrument_id") UUID instrumentId,
            String name,
            @JsonProperty("instrument_type") String instrumentType,
            String isin,
            String issuer,
            @JsonProperty("issuer_country") String issuerCountry,
            String status,
            @JsonProperty("data_availability") String dataAvailability,
            @JsonProperty("data_availability_reason") String dataAvailabilityReason,
            Map<String, Object> metadata) {

        static InstrumentDetailResponse from(FinancialInstrumentDetail detail) {
            return new InstrumentDetailResponse(
                    detail.instrumentId(),
                    detail.name(),
                    detail.instrumentType(),
                    detail.isin(),
                    detail.issuer(),
                    detail.issuerCountry(),
                    detail.status(),
                    detail.dataAvailability(),
                    detail.dataAvailabilityReason(),
                    detail.metadata());
        }
    }

    @ResponseStatus(HttpStatus.NOT_FOUND)
    static class InstrumentNotFoundException extends RuntimeException {
    }
}
