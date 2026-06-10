package com.lamprino.marketdata.persistence.jdbc;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Repository;

import com.lamprino.marketdata.domain.model.LatestListingPrice;
import com.lamprino.marketdata.domain.repository.ListingPriceRepository;

@Repository
class JdbcListingPriceRepository implements ListingPriceRepository {

    @Nullable
    private final JdbcTemplate jdbcTemplate;

    JdbcListingPriceRepository(ObjectProvider<JdbcTemplate> jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate.getIfAvailable();
    }

    @Override
    public Optional<LatestListingPrice> findLatestByListingId(UUID listingId, boolean finalOnly) {
        assertJdbcTemplateAvailable();
        return jdbcTemplate.query("""
                select l.id as listing_id, l.financial_instrument_id, l.currency_code,
                       l.data_availability, l.data_availability_reason,
                       op.price_date, op.open, op.high, op.low, op.close, op.adjusted_close,
                       op.volume, op.status as price_status, op.data_provider_code, op.retrieved_at
                from listing l
                left join lateral (
                  select price_date, open, high, low, close, adjusted_close, volume, status,
                         data_provider_code, retrieved_at
                  from observed_price
                  where listing_id = l.id
                    and (? = false or status = 'final')
                  order by price_date desc
                  limit 1
                ) op on true
                where l.id = ?
                """, (rs, rowNum) -> mapLatestListingPrice(rs, finalOnly), finalOnly, listingId)
                .stream()
                .findFirst();
    }

    private LatestListingPrice mapLatestListingPrice(ResultSet rs, boolean finalOnly) throws SQLException {
        boolean hasObservedPrice = rs.getDate("price_date") != null;
        String dataAvailability = hasObservedPrice ? rs.getString("data_availability") : "unavailable";
        String dataAvailabilityReason = rs.getString("data_availability_reason");
        if (!hasObservedPrice && dataAvailabilityReason == null) {
            dataAvailabilityReason = finalOnly ? "no_final_price" : "no_price_history";
        }
        return new LatestListingPrice(
                rs.getObject("listing_id", UUID.class),
                rs.getObject("financial_instrument_id", UUID.class),
                hasObservedPrice ? rs.getDate("price_date").toLocalDate() : null,
                rs.getBigDecimal("open"),
                rs.getBigDecimal("high"),
                rs.getBigDecimal("low"),
                rs.getBigDecimal("close"),
                rs.getBigDecimal("adjusted_close"),
                rs.getBigDecimal("volume"),
                rs.getString("currency_code"),
                rs.getString("price_status"),
                dataAvailability,
                dataAvailabilityReason,
                rs.getString("data_provider_code"),
                rs.getObject("retrieved_at", java.time.OffsetDateTime.class));
    }

    private void assertJdbcTemplateAvailable() {
        if (jdbcTemplate == null) {
            throw new IllegalStateException("Listing price reads require JDBC infrastructure");
        }
    }
}
