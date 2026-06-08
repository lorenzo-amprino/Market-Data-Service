package com.lamprino.marketdata;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
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
        jdbcTemplate.update("delete from listing");
        jdbcTemplate.update("delete from venue");
        jdbcTemplate.update("delete from financial_instrument");

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
