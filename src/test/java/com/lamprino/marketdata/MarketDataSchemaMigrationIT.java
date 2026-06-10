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
                "currency",
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
        UUID targetInstrumentId = UUID.fromString("33333333-3333-3333-3333-333333333333");

        insertReferenceData(instrumentId, listingId, now);
        insertFinancialInstrument(targetInstrumentId, "Target plc", "GB00B03MLX29", now);

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

        assertThatThrownBy(() -> jdbcTemplate.update("""
                insert into observed_price (
                  listing_id, price_date, high, low, close, status, data_provider_code,
                  retrieved_at, created_at, updated_at
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, listingId, LocalDate.parse("2026-06-03"), 110, 106, 101,
                "final", "yahoo", now, now, now))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThatThrownBy(() -> jdbcTemplate.update("""
                insert into observed_price (
                  listing_id, price_date, open, high, low, close, status, data_provider_code,
                  retrieved_at, created_at, updated_at
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, listingId, LocalDate.parse("2026-06-04"), 100, 99, 90, 101,
                "final", "yahoo", now, now, now))
                .isInstanceOf(DataIntegrityViolationException.class);

        jdbcTemplate.update("""
                insert into dividend (
                  id, financial_instrument_id, ex_date, amount, currency_code,
                  data_provider_code, retrieved_at, created_at, updated_at
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, UUID.fromString("44444444-4444-4444-4444-444444444444"), instrumentId,
                LocalDate.parse("2026-06-03"), 1.25, "EUR", "yahoo", now, now, now);

        assertThatThrownBy(() -> jdbcTemplate.update("""
                insert into dividend (
                  id, financial_instrument_id, ex_date, amount, currency_code,
                  data_provider_code, retrieved_at, created_at, updated_at
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, UUID.fromString("55555555-5555-5555-5555-555555555555"), instrumentId,
                LocalDate.parse("2026-06-03"), 1.25, "EUR", "yahoo", now, now, now))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThatThrownBy(() -> jdbcTemplate.update("""
                insert into dividend (
                  id, financial_instrument_id, ex_date, amount, currency_code,
                  data_provider_code, retrieved_at, created_at, updated_at
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, UUID.fromString("66666666-6666-6666-6666-666666666666"), instrumentId,
                LocalDate.parse("2026-06-04"), 1.25, "ZZZ", "yahoo", now, now, now))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThatThrownBy(() -> jdbcTemplate.update("""
                insert into corporate_action (
                  id, financial_instrument_id, action_type, effective_date,
                  data_provider_code, retrieved_at, created_at, updated_at
                ) values (?, ?, ?, ?, ?, ?, ?, ?)
                """, UUID.fromString("77777777-7777-7777-7777-777777777777"), instrumentId,
                "split", LocalDate.parse("2026-06-03"), "yahoo", now, now, now))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThatThrownBy(() -> jdbcTemplate.update("""
                insert into corporate_action (
                  id, financial_instrument_id, action_type, effective_date,
                  data_provider_code, retrieved_at, created_at, updated_at
                ) values (?, ?, ?, ?, ?, ?, ?, ?)
                """, UUID.fromString("88888888-8888-8888-8888-888888888888"), instrumentId,
                "merger", LocalDate.parse("2026-06-04"), "yahoo", now, now, now))
                .isInstanceOf(DataIntegrityViolationException.class);

        jdbcTemplate.update("""
                insert into corporate_action (
                  id, financial_instrument_id, action_type, effective_date, target_financial_instrument_id,
                  data_provider_code, retrieved_at, created_at, updated_at
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, UUID.fromString("99999999-9999-9999-9999-999999999999"), instrumentId,
                "merger", LocalDate.parse("2026-06-04"), targetInstrumentId, "yahoo", now, now, now);
    }

    @Test
    void v1SchemaRequiresKnownCurrenciesForBondMetadata() {
        OffsetDateTime now = OffsetDateTime.parse("2026-06-01T10:00:00Z");
        UUID instrumentId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        UUID unknownCurrencyInstrumentId = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

        insertCurrency("EUR");
        insertFinancialInstrument(instrumentId, "Republic of Italy 2030", "IT0000000001", now);
        insertFinancialInstrument(unknownCurrencyInstrumentId, "Republic of Italy 2031", "IT0000000002", now);
        insertDataProvider(now);

        jdbcTemplate.update("""
                insert into bond_metadata (
                  financial_instrument_id, maturity_date, coupon, coupon_frequency, currency_code,
                  bond_type, data_provider_code, retrieved_at, created_at, updated_at
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, instrumentId, LocalDate.parse("2030-06-01"), 2.5, "annual", "EUR",
                "government", "yahoo", now, now, now);

        assertThatThrownBy(() -> jdbcTemplate.update("""
                insert into bond_metadata (
                  financial_instrument_id, maturity_date, coupon, coupon_frequency, currency_code,
                  bond_type, data_provider_code, retrieved_at, created_at, updated_at
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, unknownCurrencyInstrumentId, LocalDate.parse("2031-06-01"), 2.5, "annual", "ZZZ",
                "government", "yahoo", now, now, now))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void v1SchemaStoresDataAvailabilityStatusOnlyForExistingSubjects() {
        OffsetDateTime now = OffsetDateTime.parse("2026-06-01T10:00:00Z");
        UUID instrumentId = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
        UUID listingId = UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd");

        insertReferenceData(instrumentId, listingId, now);

        jdbcTemplate.update("""
                insert into data_availability_status (
                  listing_id, data_type, status, checked_at, updated_at
                ) values (?, ?, ?, ?, ?)
                """, listingId, "price_history", "available", now, now);

        jdbcTemplate.update("""
                insert into data_availability_status (
                  financial_instrument_id, data_type, status, checked_at, updated_at
                ) values (?, ?, ?, ?, ?)
                """, instrumentId, "dividends", "unavailable", now, now);

        assertThatThrownBy(() -> jdbcTemplate.update("""
                insert into data_availability_status (
                  listing_id, data_type, status, checked_at, updated_at
                ) values (?, ?, ?, ?, ?)
                """, UUID.fromString("eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee"),
                "price_history", "available", now, now))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThatThrownBy(() -> jdbcTemplate.update("""
                insert into data_availability_status (
                  financial_instrument_id, listing_id, data_type, status, checked_at, updated_at
                ) values (?, ?, ?, ?, ?, ?)
                """, instrumentId, listingId, "price_history", "available", now, now))
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
        insertCurrency("EUR");
        insertFinancialInstrument(instrumentId, "Vanguard FTSE All-World UCITS ETF", uniqueIsin(instrumentId), now);
        jdbcTemplate.update("""
                insert into venue (venue_code, name, country, timezone, calendar_code, created_at, updated_at)
                values (?, ?, ?, ?, ?, ?, ?)
                on conflict do nothing
                """, "XETR", "Xetra", "DE", "Europe/Berlin", "XETR", now, now);
        jdbcTemplate.update("""
                insert into listing (
                  id, financial_instrument_id, venue_code, symbol, currency_code, status,
                  preferred, data_availability, data_availability_reason, universe_member,
                  sync_enabled, created_at, updated_at
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, listingId, instrumentId, "XETR", "L" + listingId.toString().substring(0, 8), "EUR", "active", true,
                "available", null, true, true, now, now);
        insertDataProvider(now);
    }

    private void insertCurrency(String currencyCode) {
        jdbcTemplate.update("""
                insert into currency (currency_code)
                values (?)
                on conflict do nothing
                """, currencyCode);
    }

    private void insertFinancialInstrument(UUID instrumentId, String name, String isin, OffsetDateTime now) {
        jdbcTemplate.update("""
                insert into financial_instrument (
                  id, instrument_type, name, isin, issuer, issuer_country, status,
                  data_availability, data_availability_reason, universe_member,
                  sync_enabled, created_at, updated_at
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, instrumentId, "etf", name, isin, null, null, "active", "available", null,
                true, true, now, now);
    }

    private void insertDataProvider(OffsetDateTime now) {
        jdbcTemplate.update("""
                insert into data_provider (provider_code, name, enabled, created_at, updated_at)
                values (?, ?, ?, ?, ?)
                on conflict do nothing
                """, "yahoo", "Yahoo Finance", true, now, now);
    }

    private String uniqueIsin(UUID instrumentId) {
        return instrumentId.toString().replace("-", "").substring(0, 12).toUpperCase();
    }
}
