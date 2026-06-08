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
import com.lamprino.marketdata.domain.model.FinancialInstrumentSummary;
import com.lamprino.marketdata.domain.model.ListingSummary;
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
