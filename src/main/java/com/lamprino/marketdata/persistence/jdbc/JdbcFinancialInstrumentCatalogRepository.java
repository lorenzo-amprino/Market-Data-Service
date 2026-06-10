package com.lamprino.marketdata.persistence.jdbc;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Repository;

import com.lamprino.marketdata.domain.model.FinancialInstrumentDetail;
import com.lamprino.marketdata.domain.model.FinancialInstrumentLookup;
import com.lamprino.marketdata.domain.model.FinancialInstrumentSummary;
import com.lamprino.marketdata.domain.model.ListingDetail;
import com.lamprino.marketdata.domain.model.ListingSummary;
import com.lamprino.marketdata.domain.model.VenueSummary;
import com.lamprino.marketdata.domain.repository.FinancialInstrumentCatalogRepository;

@Repository
class JdbcFinancialInstrumentCatalogRepository implements FinancialInstrumentCatalogRepository {

    @Nullable
    private final JdbcTemplate jdbcTemplate;

    JdbcFinancialInstrumentCatalogRepository(ObjectProvider<JdbcTemplate> jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate.getIfAvailable();
    }

    @Override
    public List<FinancialInstrumentSummary> search(String query, int limit) {
        assertJdbcTemplateAvailable();
        String pattern = "%" + query.toLowerCase() + "%";
        List<FinancialInstrumentRow> instrumentRows = jdbcTemplate.query("""
                select distinct fi.id, fi.name, fi.instrument_type, fi.isin, fi.status, fi.data_availability
                from financial_instrument fi
                left join listing l on l.financial_instrument_id = fi.id
                where lower(fi.name) like ?
                   or lower(coalesce(fi.isin, '')) like ?
                   or lower(coalesce(fi.issuer, '')) like ?
                   or lower(coalesce(l.symbol, '')) like ?
                   or lower(coalesce(l.venue_code, '')) like ?
                order by fi.name asc, fi.id asc
                limit ?
                """, this::mapInstrumentRow, pattern, pattern, pattern, pattern, pattern, limit);

        if (instrumentRows.isEmpty()) {
            return List.of();
        }

        Map<UUID, List<ListingSummary>> listingsByInstrumentId = listingsByInstrumentId(
                instrumentRows.stream().map(FinancialInstrumentRow::instrumentId).toList());
        return instrumentRows.stream()
                .map(row -> new FinancialInstrumentSummary(
                        row.instrumentId(),
                        row.name(),
                        row.instrumentType(),
                        row.isin(),
                        row.status(),
                        row.dataAvailability(),
                        listingsByInstrumentId.getOrDefault(row.instrumentId(), List.of())))
                .toList();
    }

    @Override
    public Optional<FinancialInstrumentDetail> findById(UUID instrumentId) {
        assertJdbcTemplateAvailable();
        return jdbcTemplate.query("""
                select id, name, instrument_type, isin, issuer, issuer_country, status,
                       data_availability, data_availability_reason
                from financial_instrument
                where id = ?
                """, (rs, rowNum) -> new FinancialInstrumentDetail(
                        rs.getObject("id", UUID.class),
                        rs.getString("name"),
                        rs.getString("instrument_type"),
                        rs.getString("isin"),
                        rs.getString("issuer"),
                        rs.getString("issuer_country"),
                        rs.getString("status"),
                        rs.getString("data_availability"),
                        rs.getString("data_availability_reason"),
                        Map.of()), instrumentId)
                .stream()
                .findFirst();
    }

    @Override
    public boolean existsById(UUID instrumentId) {
        assertJdbcTemplateAvailable();
        Boolean exists = jdbcTemplate.queryForObject("""
                select exists(select 1 from financial_instrument where id = ?)
                """, Boolean.class, instrumentId);
        return Boolean.TRUE.equals(exists);
    }

    @Override
    public List<ListingDetail> findListingsByInstrumentId(UUID instrumentId) {
        assertJdbcTemplateAvailable();
        return jdbcTemplate.query("""
                select l.id as listing_id, l.symbol, l.currency_code, l.status as listing_status,
                       l.preferred, l.data_availability, l.data_availability_reason,
                       v.venue_code, v.name as venue_name, v.country, v.timezone, v.calendar_code
                from listing l
                join venue v on v.venue_code = l.venue_code
                where l.financial_instrument_id = ?
                order by l.preferred desc, l.venue_code asc, l.symbol asc
                """, (rs, rowNum) -> new ListingDetail(
                        rs.getObject("listing_id", UUID.class),
                        rs.getString("symbol"),
                        rs.getString("currency_code"),
                        rs.getString("listing_status"),
                        rs.getBoolean("preferred"),
                        rs.getString("data_availability"),
                        rs.getString("data_availability_reason"),
                        new VenueSummary(
                                rs.getString("venue_code"),
                                rs.getString("venue_name"),
                                rs.getString("country"),
                                rs.getString("timezone"),
                                rs.getString("calendar_code"))), instrumentId);
    }

    @Override
    public Optional<FinancialInstrumentLookup> findByIsin(String isin) {
        assertJdbcTemplateAvailable();
        return jdbcTemplate.query("""
                select id, name, instrument_type, isin, status, data_availability
                from financial_instrument
                where isin = ?
                """, (rs, rowNum) -> new FinancialInstrumentLookup(
                        "isin",
                        mapInstrumentSummaryWithoutListings(rs),
                        null), isin)
                .stream()
                .findFirst();
    }

    @Override
    public Optional<FinancialInstrumentLookup> findByVenueCodeAndSymbol(String venueCode, String symbol) {
        assertJdbcTemplateAvailable();
        return jdbcTemplate.query("""
                select fi.id, fi.name, fi.instrument_type, fi.isin, fi.status, fi.data_availability,
                       l.id as listing_id, l.venue_code, l.symbol, l.currency_code, l.status as listing_status, l.preferred
                from listing l
                join financial_instrument fi on fi.id = l.financial_instrument_id
                where l.venue_code = ? and l.symbol = ?
                """, (rs, rowNum) -> new FinancialInstrumentLookup(
                        "venue_symbol",
                        mapInstrumentSummaryWithoutListings(rs),
                        mapListingSummary(rs)), venueCode, symbol)
                .stream()
                .findFirst();
    }

    @Override
    public Optional<FinancialInstrumentLookup> findByProviderIdentifier(String providerCode, String providerIdentifier) {
        assertJdbcTemplateAvailable();
        return jdbcTemplate.query("""
                select fi.id, fi.name, fi.instrument_type, fi.isin, fi.status, fi.data_availability,
                       l.id as listing_id, l.venue_code, l.symbol, l.currency_code, l.status as listing_status, l.preferred
                from provider_identifier pi
                left join listing l on l.id = pi.listing_id
                join financial_instrument fi on fi.id = coalesce(pi.financial_instrument_id, l.financial_instrument_id)
                where pi.provider_code = ? and pi.provider_identifier = ?
                """, (rs, rowNum) -> new FinancialInstrumentLookup(
                        "provider_identifier",
                        mapInstrumentSummaryWithoutListings(rs),
                        mapNullableListingSummary(rs)), providerCode, providerIdentifier)
                .stream()
                .findFirst();
    }

    private void assertJdbcTemplateAvailable() {
        if (jdbcTemplate == null) {
            throw new IllegalStateException("Financial Instrument catalog requires JDBC infrastructure");
        }
    }

    private FinancialInstrumentRow mapInstrumentRow(ResultSet rs, int rowNum) throws SQLException {
        return new FinancialInstrumentRow(
                rs.getObject("id", UUID.class),
                rs.getString("name"),
                rs.getString("instrument_type"),
                rs.getString("isin"),
                rs.getString("status"),
                rs.getString("data_availability"));
    }

    private FinancialInstrumentSummary mapInstrumentSummaryWithoutListings(ResultSet rs) throws SQLException {
        return new FinancialInstrumentSummary(
                rs.getObject("id", UUID.class),
                rs.getString("name"),
                rs.getString("instrument_type"),
                rs.getString("isin"),
                rs.getString("status"),
                rs.getString("data_availability"),
                List.of());
    }

    private ListingSummary mapListingSummary(ResultSet rs) throws SQLException {
        return new ListingSummary(
                rs.getObject("listing_id", UUID.class),
                rs.getString("venue_code"),
                rs.getString("symbol"),
                rs.getString("currency_code"),
                rs.getString("listing_status"),
                rs.getBoolean("preferred"));
    }

    private ListingSummary mapNullableListingSummary(ResultSet rs) throws SQLException {
        UUID listingId = rs.getObject("listing_id", UUID.class);
        if (listingId == null) {
            return null;
        }
        return mapListingSummary(rs);
    }

    private Map<UUID, List<ListingSummary>> listingsByInstrumentId(List<UUID> instrumentIds) {
        Map<UUID, List<ListingSummary>> listingsByInstrumentId = new LinkedHashMap<>();
        for (UUID instrumentId : instrumentIds) {
            listingsByInstrumentId.put(instrumentId, new ArrayList<>());
        }

        jdbcTemplate.query("""
                select financial_instrument_id, id, venue_code, symbol, currency_code, status, preferred
                from listing
                where financial_instrument_id = any (?::uuid[])
                order by preferred desc, venue_code asc, symbol asc
                """, rs -> {
                    UUID instrumentId = rs.getObject("financial_instrument_id", UUID.class);
                    listingsByInstrumentId.get(instrumentId).add(new ListingSummary(
                            rs.getObject("id", UUID.class),
                            rs.getString("venue_code"),
                            rs.getString("symbol"),
                            rs.getString("currency_code"),
                            rs.getString("status"),
                            rs.getBoolean("preferred")));
                }, (Object) instrumentIds.toArray(UUID[]::new));

        return listingsByInstrumentId;
    }

    private record FinancialInstrumentRow(
            UUID instrumentId,
            String name,
            String instrumentType,
            String isin,
            String status,
            String dataAvailability) {
    }
}
