package com.lamprino.marketdata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class FinancialInstrumentCatalogIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final UUID instrumentId = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private final UUID listingId = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("delete from observed_price");
        jdbcTemplate.update("delete from provider_identifier");
        jdbcTemplate.update("delete from listing");
        jdbcTemplate.update("delete from venue");
        jdbcTemplate.update("delete from financial_instrument");
        jdbcTemplate.update("delete from data_provider");

        OffsetDateTime now = OffsetDateTime.parse("2026-06-01T10:00:00Z");
        jdbcTemplate.update("""
                insert into financial_instrument (
                  id, instrument_type, name, isin, issuer, issuer_country, status,
                  data_availability, data_availability_reason, universe_member,
                  sync_enabled, created_at, updated_at
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, instrumentId, "etf", "Vanguard FTSE All-World UCITS ETF", "IE00BK5BQT80",
                null, null, "active", "available", null, true, true, now, now);
        jdbcTemplate.update("""
                insert into venue (venue_code, name, country, timezone, calendar_code, created_at, updated_at)
                values (?, ?, ?, ?, ?, ?, ?)
                """, "XETR", "Xetra", "DE", "Europe/Berlin", "XETR", now, now);
        jdbcTemplate.update("""
                insert into listing (
                  id, financial_instrument_id, venue_code, symbol, currency_code, status,
                  preferred, data_availability, data_availability_reason, universe_member,
                  sync_enabled, created_at, updated_at
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, listingId, instrumentId, "XETR", "VWCE", "EUR", "active", true,
                "available", null, true, true, now, now);
        jdbcTemplate.update("""
                insert into data_provider (provider_code, name, enabled, created_at, updated_at)
                values (?, ?, ?, ?, ?)
                """, "yahoo", "Yahoo Finance", true, now, now);
        jdbcTemplate.update("""
                insert into provider_identifier (
                  id, provider_code, provider_identifier, financial_instrument_id, listing_id, created_at, updated_at
                ) values (?, ?, ?, ?, ?, ?, ?)
                """, UUID.fromString("44444444-4444-4444-4444-444444444444"), "yahoo", "VWCE.DE", null, listingId, now, now);
    }

    @Test
    void looksUpFinancialInstrumentByIsin() {
        ResponseEntity<Map> response = restTemplate.getForEntity("/instruments/lookup?isin={isin}", Map.class, "IE00BK5BQT80");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("instrument_id", instrumentId.toString());
        assertThat(response.getBody()).containsEntry("matched_by", "isin");
        assertThat(response.getBody()).containsKey("instrument");
        assertThat(response.getBody()).containsEntry("listing", null);
    }

    @Test
    void rejectsDuplicateIsinWhenPresent() {
        OffsetDateTime now = OffsetDateTime.parse("2026-06-01T10:00:00Z");

        assertThatThrownBy(() -> jdbcTemplate.update("""
                insert into financial_instrument (
                  id, instrument_type, name, isin, status, data_availability,
                  universe_member, sync_enabled, created_at, updated_at
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, UUID.fromString("55555555-5555-5555-5555-555555555555"), "etf",
                "Duplicate ISIN ETF", "IE00BK5BQT80", "active", "available", false, true, now, now))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void looksUpFinancialInstrumentByVenueAndListingSymbol() {
        ResponseEntity<Map> response = restTemplate.getForEntity(
                "/instruments/lookup?venue_code={venueCode}&symbol={symbol}", Map.class, "XETR", "VWCE");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("instrument_id", instrumentId.toString());
        assertThat(response.getBody()).containsEntry("matched_by", "venue_symbol");
        Map<?, ?> listing = (Map<?, ?>) response.getBody().get("listing");
        assertThat(listing.get("listing_id")).isEqualTo(listingId.toString());
        assertThat(listing.get("venue_code")).isEqualTo("XETR");
        assertThat(listing.get("symbol")).isEqualTo("VWCE");
    }

    @Test
    void rejectsDuplicateListingSymbolsWithinVenue() {
        OffsetDateTime now = OffsetDateTime.parse("2026-06-01T10:00:00Z");

        assertThatThrownBy(() -> jdbcTemplate.update("""
                insert into listing (
                  id, financial_instrument_id, venue_code, symbol, currency_code, status,
                  preferred, data_availability, universe_member, sync_enabled, created_at, updated_at
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, UUID.fromString("66666666-6666-6666-6666-666666666666"), instrumentId,
                "XETR", "VWCE", "EUR", "active", false, "available", false, true, now, now))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void looksUpFinancialInstrumentByProviderIdentifier() {
        ResponseEntity<Map> response = restTemplate.getForEntity(
                "/instruments/lookup?provider={provider}&provider_identifier={providerIdentifier}",
                Map.class, "yahoo", "VWCE.DE");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("instrument_id", instrumentId.toString());
        assertThat(response.getBody()).containsEntry("matched_by", "provider_identifier");
        Map<?, ?> listing = (Map<?, ?>) response.getBody().get("listing");
        assertThat(listing.get("listing_id")).isEqualTo(listingId.toString());
    }

    @Test
    void returnsNotFoundWhenExactLookupDoesNotMatchALocalResource() {
        ResponseEntity<Map> response = restTemplate.getForEntity("/instruments/lookup?isin={isin}", Map.class, "missing");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        Map<?, ?> error = (Map<?, ?>) response.getBody().get("error");
        assertThat(error.get("code")).isEqualTo("instrument_not_found");
    }

    @Test
    void searchesFinancialInstrumentsFromLocalDataOnly() {
        ResponseEntity<Map> response = restTemplate.getForEntity("/instruments/search?q=vwce", Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsKey("items");
        assertThat((Iterable<?>) response.getBody().get("items"))
                .singleElement()
                .satisfies(item -> {
                    Map<?, ?> instrument = (Map<?, ?>) item;
                    assertThat(instrument.get("instrument_id")).isEqualTo(instrumentId.toString());
                    assertThat(instrument.get("name")).isEqualTo("Vanguard FTSE All-World UCITS ETF");
                    assertThat(instrument.get("instrument_type")).isEqualTo("etf");
                    assertThat(instrument.get("isin")).isEqualTo("IE00BK5BQT80");
                    assertThat(instrument.get("status")).isEqualTo("active");
                    assertThat((Iterable<?>) instrument.get("listings"))
                            .singleElement()
                            .satisfies(listingItem -> {
                                Map<?, ?> listing = (Map<?, ?>) listingItem;
                                assertThat(listing.get("listing_id")).isEqualTo(listingId.toString());
                                assertThat(listing.get("venue_code")).isEqualTo("XETR");
                                assertThat(listing.get("symbol")).isEqualTo("VWCE");
                                assertThat(listing.get("preferred")).isEqualTo(true);
                            });
                });
    }

    @Test
    void listsFinancialInstrumentListingsWithVenueMarketCalendarFields() {
        OffsetDateTime now = OffsetDateTime.parse("2026-06-01T10:00:00Z");
        UUID milanListingId = UUID.fromString("77777777-7777-7777-7777-777777777777");
        jdbcTemplate.update("""
                insert into venue (venue_code, name, country, timezone, calendar_code, created_at, updated_at)
                values (?, ?, ?, ?, ?, ?, ?)
                """, "XMIL", "Borsa Italiana", "IT", "Europe/Rome", "XMIL", now, now);
        jdbcTemplate.update("""
                insert into listing (
                  id, financial_instrument_id, venue_code, symbol, currency_code, status,
                  preferred, data_availability, data_availability_reason, universe_member,
                  sync_enabled, created_at, updated_at
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, milanListingId, instrumentId, "XMIL", "VWCE", "EUR", "suspended", false,
                "stale", "market_calendar_closed", false, true, now, now);

        ResponseEntity<Map> response = restTemplate.getForEntity("/instruments/{id}/listings", Map.class, instrumentId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("instrument_id", instrumentId.toString());
        assertThat((Iterable<?>) response.getBody().get("items"))
                .hasSize(2)
                .anySatisfy(item -> {
                    Map<?, ?> listing = (Map<?, ?>) item;
                    assertThat(listing.get("listing_id")).isEqualTo(listingId.toString());
                    assertThat(listing.get("symbol")).isEqualTo("VWCE");
                    assertThat(listing.get("currency")).isEqualTo("EUR");
                    assertThat(listing.get("status")).isEqualTo("active");
                    assertThat(listing.get("preferred")).isEqualTo(true);
                    assertThat(listing.get("data_availability")).isEqualTo("available");
                    assertThat(listing.get("data_availability_reason")).isNull();
                    Map<?, ?> venue = (Map<?, ?>) listing.get("venue");
                    assertThat(venue.get("venue_code")).isEqualTo("XETR");
                    assertThat(venue.get("name")).isEqualTo("Xetra");
                    assertThat(venue.get("country")).isEqualTo("DE");
                    assertThat(venue.get("timezone")).isEqualTo("Europe/Berlin");
                    assertThat(venue.get("calendar_code")).isEqualTo("XETR");
                    assertThat(listing.containsKey("name")).isFalse();
                    assertThat(listing.containsKey("isin")).isFalse();
                    assertThat(listing.containsKey("instrument_type")).isFalse();
                })
                .anySatisfy(item -> {
                    Map<?, ?> listing = (Map<?, ?>) item;
                    assertThat(listing.get("listing_id")).isEqualTo(milanListingId.toString());
                    assertThat(listing.get("status")).isEqualTo("suspended");
                    Map<?, ?> venue = (Map<?, ?>) listing.get("venue");
                    assertThat(venue.get("venue_code")).isEqualTo("XMIL");
                    assertThat(venue.get("calendar_code")).isEqualTo("XMIL");
                });
    }

    @Test
    void returnsNotFoundWhenListingParentFinancialInstrumentIsMissing() {
        UUID missingId = UUID.fromString("33333333-3333-3333-3333-333333333333");

        ResponseEntity<Map> response = restTemplate.getForEntity("/instruments/{id}/listings", Map.class, missingId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        Map<?, ?> error = (Map<?, ?>) response.getBody().get("error");
        assertThat(error.get("code")).isEqualTo("instrument_not_found");
    }

    @Test
    void returnsFreshestLatestListingPriceWithDataProvenance() {
        OffsetDateTime retrievedAt = OffsetDateTime.parse("2026-06-02T18:10:00Z");
        insertObservedPrice("2026-06-01", "final", "8.10", "8.20", "8.05", "8.18", "8.12", "1200000", retrievedAt.minusDays(1));
        insertObservedPrice("2026-06-02", "provisional", "8.18", "8.30", "8.15", "8.25", null, "900000", retrievedAt);

        ResponseEntity<Map> response = restTemplate.getForEntity(
                "/listings/{id}/prices/latest", Map.class, listingId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("listing_id", listingId.toString());
        assertThat(response.getBody()).containsEntry("instrument_id", instrumentId.toString());
        assertThat(response.getBody()).containsEntry("date", "2026-06-02");
        assertThat(response.getBody()).containsEntry("price", 8.25);
        assertThat(response.getBody()).containsEntry("price_kind", "close");
        assertThat(response.getBody()).containsEntry("close", 8.25);
        assertThat(response.getBody()).containsEntry("currency", "EUR");
        assertThat(response.getBody()).containsEntry("status", "provisional");
        assertThat(response.getBody()).containsEntry("data_availability", "available");
        assertThat(response.getBody()).containsEntry("data_availability_reason", null);
        assertThat(response.getBody()).containsEntry("data_provider", "yahoo");
        assertThat(response.getBody()).containsEntry("retrieved_at", "2026-06-02T18:10:00Z");
    }

    @Test
    void canRequestLatestFinalListingPriceOnly() {
        OffsetDateTime retrievedAt = OffsetDateTime.parse("2026-06-02T18:10:00Z");
        insertObservedPrice("2026-06-01", "final", "8.10", "8.20", "8.05", "8.18", "8.12", "1200000", retrievedAt.minusDays(1));
        insertObservedPrice("2026-06-02", "provisional", "8.18", "8.30", "8.15", "8.25", null, "900000", retrievedAt);

        ResponseEntity<Map> response = restTemplate.getForEntity(
                "/listings/{id}/prices/latest?status=final", Map.class, listingId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("date", "2026-06-01");
        assertThat(response.getBody()).containsEntry("status", "final");
        assertThat(response.getBody()).containsEntry("price", 8.18);
    }

    @Test
    void returnsDataAvailabilityWhenExistingListingHasNoUsablePrice() {
        jdbcTemplate.update("""
                update listing
                set data_availability = ?, data_availability_reason = ?
                where id = ?
                """, "unavailable", "provider_unavailable", listingId);

        ResponseEntity<Map> response = restTemplate.getForEntity(
                "/listings/{id}/prices/latest", Map.class, listingId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("listing_id", listingId.toString());
        assertThat(response.getBody()).containsEntry("instrument_id", instrumentId.toString());
        assertThat(response.getBody()).containsEntry("price", null);
        assertThat(response.getBody()).containsEntry("data_availability", "unavailable");
        assertThat(response.getBody()).containsEntry("data_availability_reason", "provider_unavailable");
        assertThat(response.getBody()).containsEntry("date", null);
        assertThat(response.getBody()).containsEntry("data_provider", null);
    }

    @Test
    void returnsNotFoundWhenLatestPriceListingIsMissing() {
        UUID missingId = UUID.fromString("33333333-3333-3333-3333-333333333333");

        ResponseEntity<Map> response = restTemplate.getForEntity(
                "/listings/{id}/prices/latest", Map.class, missingId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        Map<?, ?> error = (Map<?, ?>) response.getBody().get("error");
        assertThat(error.get("code")).isEqualTo("listing_not_found");
    }

    private void insertObservedPrice(
            String priceDate,
            String status,
            String open,
            String high,
            String low,
            String close,
            String adjustedClose,
            String volume,
            OffsetDateTime retrievedAt) {
        jdbcTemplate.update("""
                insert into observed_price (
                  listing_id, price_date, open, high, low, close, adjusted_close, volume,
                  status, data_provider_code, retrieved_at, created_at, updated_at
                ) values (?, ?::date, ?::numeric, ?::numeric, ?::numeric, ?::numeric, ?::numeric, ?::numeric, ?, ?, ?, ?, ?)
                """, listingId, priceDate, open, high, low, close, adjustedClose, volume,
                status, "yahoo", retrievedAt, retrievedAt, retrievedAt);
    }

    @Test
    void returnsFinancialInstrumentDetailsByCanonicalUuid() {
        ResponseEntity<Map> response = restTemplate.getForEntity("/instruments/{id}", Map.class, instrumentId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("instrument_id", instrumentId.toString());
        assertThat(response.getBody()).containsEntry("instrument_type", "etf");
        assertThat(response.getBody()).containsEntry("status", "active");
        assertThat(response.getBody()).containsEntry("data_availability", "available");
    }

    @Test
    void returnsNotFoundForMissingFinancialInstrument() {
        UUID missingId = UUID.fromString("33333333-3333-3333-3333-333333333333");

        ResponseEntity<Map> response = restTemplate.getForEntity("/instruments/{id}", Map.class, missingId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        Map<?, ?> error = (Map<?, ?>) response.getBody().get("error");
        assertThat(error.get("code")).isEqualTo("instrument_not_found");
        assertThat(error.get("message")).isEqualTo("Financial Instrument not found");
    }

    @Test
    void emptySearchStillReturnsOkWithoutAConfiguredDataProvider() {
        ResponseEntity<Map> response = restTemplate.getForEntity("/instruments/search?q=does-not-exist", Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat((Iterable<?>) response.getBody().get("items")).isEmpty();
    }
}
