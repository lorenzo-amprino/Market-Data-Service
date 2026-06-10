package com.lamprino.marketdata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest
@ActiveProfiles("test")
class MarketDataSchemaMigrationIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void migrationsCreateTheV1MarketDataSchemaOnAnEmptyPostgreSqlDatabase() {
        assertThat(missingTables(List.of(
                "financial_instrument",
                "venue",
                "listing",
                "data_provider",
                "provider_identifier",
                "observed_price",
                "dividend",
                "corporate_action",
                "etf_metadata",
                "bond_metadata",
                "instrument_discovery_request",
                "provider_raw_response",
                "data_availability_status"))).isEmpty();
    }

    @Test
    void migrationsDoNotCreateDeferredV1Tables() {
        List<String> deferredTables = jdbcTemplate.queryForList("""
                select table_name
                from information_schema.tables
                where table_schema = 'public'
                  and (
                    table_name in ('latest_price', 'ingestion_job', 'ingestion_job_item')
                    or table_name like 'market_calendar%'
                  )
                order by table_name
                """, String.class);

        assertThat(deferredTables).isEmpty();
    }

    @Test
    void v1SchemaEnforcesRepresentativeUniquenessAndCheckConstraints() {
        OffsetDateTime now = OffsetDateTime.parse("2026-06-01T10:00:00Z");
        UUID instrumentId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID listingId = UUID.fromString("22222222-2222-2222-2222-222222222222");

        insertReferenceData(instrumentId, listingId, now);

        jdbcTemplate.update("""
                insert into observed_price (
                  listing_id, price_date, open, high, low, close, adjusted_close,
                  volume, status, data_provider_code, retrieved_at, created_at, updated_at
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, listingId, LocalDate.parse("2026-06-01"), 100, 110, 90, 105, 104,
                1000, "final", "yahoo", now, now, now);

        assertThatThrownBy(() -> jdbcTemplate.update("""
                insert into observed_price (
                  listing_id, price_date, close, status, data_provider_code,
                  retrieved_at, created_at, updated_at
                ) values (?, ?, ?, ?, ?, ?, ?, ?)
                """, listingId, LocalDate.parse("2026-06-01"), 106, "final", "yahoo", now, now, now))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThatThrownBy(() -> jdbcTemplate.update("""
                insert into observed_price (
                  listing_id, price_date, close, status, data_provider_code,
                  retrieved_at, created_at, updated_at
                ) values (?, ?, ?, ?, ?, ?, ?, ?)
                """, listingId, LocalDate.parse("2026-06-02"), -1, "final", "yahoo", now, now, now))
                .isInstanceOf(DataIntegrityViolationException.class);

        jdbcTemplate.update("""
                insert into dividend (
                  id, financial_instrument_id, ex_date, amount, currency_code,
                  data_provider_code, retrieved_at, created_at, updated_at
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, UUID.fromString("33333333-3333-3333-3333-333333333333"), instrumentId,
                LocalDate.parse("2026-06-03"), 1.25, "EUR", "yahoo", now, now, now);

        assertThatThrownBy(() -> jdbcTemplate.update("""
                insert into dividend (
                  id, financial_instrument_id, ex_date, amount, currency_code,
                  data_provider_code, retrieved_at, created_at, updated_at
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, UUID.fromString("44444444-4444-4444-4444-444444444444"), instrumentId,
                LocalDate.parse("2026-06-03"), 1.25, "EUR", "yahoo", now, now, now))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThatThrownBy(() -> jdbcTemplate.update("""
                insert into corporate_action (
                  id, financial_instrument_id, action_type, effective_date,
                  data_provider_code, retrieved_at, created_at, updated_at
                ) values (?, ?, ?, ?, ?, ?, ?, ?)
                """, UUID.fromString("55555555-5555-5555-5555-555555555555"), instrumentId,
                "split", LocalDate.parse("2026-06-03"), "yahoo", now, now, now))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThatThrownBy(() -> jdbcTemplate.update("""
                insert into data_availability_status (
                  subject_type, subject_id, data_type, status, checked_at, updated_at
                ) values (?, ?, ?, ?, ?, ?)
                """, "listing", listingId, "intraday_price", "available", now, now))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private List<String> missingTables(List<String> tableNames) {
        return tableNames.stream()
                .filter(tableName -> !tableExists(tableName))
                .toList();
    }

    private boolean tableExists(String tableName) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject(
                "select to_regclass(?) is not null", Boolean.class, "public." + tableName));
    }

    private void insertReferenceData(UUID instrumentId, UUID listingId, OffsetDateTime now) {
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
    }
}
