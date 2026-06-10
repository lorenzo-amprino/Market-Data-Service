# Market Data Service API Design

## Purpose

This document describes the v1 internal HTTP API design for Market Data Service. APIs expose canonical Financial Instruments, Listings, daily Observed Prices, dividends, Data Availability, and explicit discovery/acquisition workflows for instruments not yet known locally.

## Design constraints from ADRs

- Canonical API paths use internal UUID resource identifiers.
- Natural identifiers such as ISIN, venue code plus symbol, and provider identifiers are resolved through lookup endpoints.
- Canonical read APIs serve local database data and do not call external Data Providers synchronously.
- Explicit Discovery Search endpoints may call Data Providers synchronously and return best-effort candidates.
- Acquisition/backfill happens through asynchronous Instrument Discovery Requests.
- Missing resources return `404`; unavailable data for existing resources returns `200` with explicit Data Availability.
- Instrument-level price endpoints use the Preferred Listing.
- Prices are daily observed prices in the Listing currency.
- FX conversion, realtime quotes, intraday bars, and fixed-income analytics are outside v1.

## Response style

V1 uses direct JSON responses rather than a mandatory global `{ data, meta }` envelope. Endpoint-specific metadata such as pagination, Data Availability, Data Provenance, or retrieval timestamps is included where it adds value.

Collection responses use an explicit `items` array and may include pagination metadata when needed.

Example collection response:

```json
{
  "items": [],
  "page": {
    "limit": 50,
    "next_cursor": null
  }
}
```

## Error response style

V1 uses a simple structured error shape rather than RFC 7807.

```json
{
  "error": {
    "code": "instrument_not_found",
    "message": "Financial Instrument not found",
    "details": {}
  }
}
```

Common error codes include:

- `instrument_not_found`
- `listing_not_found`
- `lookup_ambiguous`
- `invalid_request`
- `discovery_unavailable`
- `provider_rate_limited`

## Pagination and ordering

Generic collection endpoints use cursor pagination with `limit` and `cursor`. The default `limit` is 50 unless an endpoint specifies otherwise.

Time-series endpoints use explicit date ranges with `from` and `to` query parameters and return records ordered by date ascending by default. A `limit` parameter may be added to protect large responses, but date range filtering is the primary control.

## Endpoints

### Local instrument search

```http
GET /instruments/search?q={query}&limit={limit}&cursor={cursor}
```

Searches only canonical local database records. This endpoint does not call external Data Providers synchronously. If no local matches are found, the endpoint returns `200` with an empty `items` array.

Response:

```json
{
  "items": [
    {
      "instrument_id": "uuid",
      "name": "Vanguard FTSE All-World UCITS ETF",
      "instrument_type": "etf",
      "isin": "IE00BK5BQT80",
      "status": "active",
      "data_availability": "available",
      "listings": [
        {
          "listing_id": "uuid",
          "venue_code": "XETR",
          "symbol": "VWCE",
          "currency": "EUR",
          "status": "active",
          "preferred": true
        }
      ]
    }
  ],
  "page": {
    "limit": 50,
    "next_cursor": null
  }
}
```

### Discovery Search

```http
GET /instrument-discovery/search?q={query}&limit={limit}
```

Searches external Data Providers synchronously for best-effort candidate Financial Instruments or Listings that may not be known locally. Results are not canonical Market Data Service records until acquired through an Instrument Discovery Request. The endpoint may be rate-limited, disabled by provider or environment, and may return partial or incomplete results.

If a candidate appears to match an already-known local record, `already_known` and the known IDs are populated.

Response:

```json
{
  "items": [
    {
      "provider": "yahoo",
      "provider_identifier": "VWCE.DE",
      "name": "Vanguard FTSE All-World UCITS ETF Acc",
      "instrument_type": "etf",
      "isin": "IE00BK5BQT80",
      "venue_code": "XETR",
      "symbol": "VWCE",
      "currency": "EUR",
      "confidence": "high",
      "already_known": false,
      "known_instrument_id": null,
      "known_listing_id": null
    }
  ]
}
```

`isin`, `venue_code`, `symbol`, and `currency` may be null when a provider search result is incomplete. `confidence` is one of `high`, `medium`, or `low`.

### Create Instrument Discovery Request

```http
POST /instrument-discovery-requests
```

Creates an asynchronous acquisition request for a selected Discovery Search candidate or directly supplied provider identifier.

Request:

```json
{
  "provider": "yahoo",
  "provider_identifier": "VWCE.DE",
  "add_to_universe": true
}
```

Response: `202 Accepted`

```json
{
  "request_id": "uuid",
  "status": "pending"
}
```

If `add_to_universe=true`, successful acquisition adds the created instrument/listing to the Universe and starts bounded ingestion/backfill. If `add_to_universe=false`, acquisition creates only canonical/reference records unless a separate explicit backfill request is introduced.

### Get Instrument Discovery Request

```http
GET /instrument-discovery-requests/{request_id}
```

Returns the current state and result of an asynchronous acquisition request.

Response:

```json
{
  "request_id": "uuid",
  "status": "completed",
  "result": {
    "instrument_id": "uuid",
    "listing_id": "uuid"
  },
  "failure_reason": null,
  "created_at": "2026-06-01T10:00:00Z",
  "completed_at": "2026-06-01T10:01:30Z"
}
```

### Exact instrument lookup

```http
GET /instruments/lookup?isin={isin}
GET /instruments/lookup?venue_code={venue_code}&symbol={symbol}
GET /instruments/lookup?provider={provider}&provider_identifier={provider_identifier}
```

Resolves exact natural or provider-specific identifiers against canonical local database records. This endpoint does not call external Data Providers synchronously.

Response:

```json
{
  "instrument_id": "uuid",
  "matched_by": "venue_symbol",
  "instrument": {
    "instrument_id": "uuid",
    "name": "Enel Spa",
    "instrument_type": "stock",
    "isin": "IT0003128367",
    "status": "active",
    "data_availability": "available"
  },
  "listing": {
    "listing_id": "uuid",
    "venue_code": "XMIL",
    "symbol": "ENEL",
    "currency": "EUR",
    "status": "active",
    "preferred": true
  }
}
```

For `isin` lookup, `listing` is `null` unless a future parameter asks for a Preferred Listing. For `venue_code + symbol` lookup, `listing` is the matched Listing. For `provider + provider_identifier` lookup, `listing` is populated only when the provider identifier maps to a Listing.

Ambiguous lookup returns `409 Conflict` with `lookup_ambiguous`. Lookup miss returns `404` because no canonical local resource matches the exact identifier.

### Get Financial Instrument detail

```http
GET /instruments/{instrument_id}
```

Returns canonical Financial Instrument details and instrument-type-specific metadata. Listings are not embedded; use the listings endpoint to retrieve them.

Response:

```json
{
  "instrument_id": "uuid",
  "name": "Vanguard FTSE All-World UCITS ETF",
  "instrument_type": "etf",
  "isin": "IE00BK5BQT80",
  "issuer": null,
  "issuer_country": null,
  "status": "active",
  "data_availability": "available",
  "data_availability_reason": null,
  "metadata": {
    "fund_manager": "Vanguard",
    "domicile_country": "IE",
    "ter": 0.22,
    "replication": "physical",
    "distribution_policy": "accumulating",
    "benchmark": "FTSE All-World",
    "index_provider": "FTSE Russell"
  }
}
```

`metadata` is polymorphic by `instrument_type`; unavailable metadata fields may be null. Missing instrument returns `404` with `instrument_not_found`.

### List Financial Instrument Listings

```http
GET /instruments/{instrument_id}/listings
```

Returns Listings for a Financial Instrument. The response is not paginated in v1 because a single instrument is expected to have a small number of Listings.

Response:

```json
{
  "instrument_id": "uuid",
  "items": [
    {
      "listing_id": "uuid",
      "venue": {
        "venue_code": "XETR",
        "name": "Xetra",
        "country": "DE",
        "timezone": "Europe/Berlin",
        "calendar_code": "XETR"
      },
      "symbol": "VWCE",
      "currency": "EUR",
      "status": "active",
      "preferred": true,
      "data_availability": "available",
      "data_availability_reason": null
    }
  ]
}
```

Missing instrument returns `404` with `instrument_not_found`.

### Get latest Listing price

```http
GET /listings/{listing_id}/prices/latest?status={status}
```

Returns the freshest available daily Observed Price for a Listing. By default, the endpoint may return a provisional or final price. If `status=final`, it returns the latest final price only.

Available response:

```json
{
  "listing_id": "uuid",
  "instrument_id": "uuid",
  "date": "2026-06-01",
  "price": 8.18,
  "price_kind": "close",
  "open": 8.10,
  "high": 8.20,
  "low": 8.05,
  "close": 8.18,
  "adjusted_close": 8.12,
  "volume": 1200000,
  "currency": "EUR",
  "status": "final",
  "data_availability": "available",
  "data_availability_reason": null,
  "data_provider": "yahoo",
  "retrieved_at": "2026-06-01T18:10:00Z"
}
```

Unavailable-data response for an existing Listing:

```json
{
  "listing_id": "uuid",
  "instrument_id": "uuid",
  "price": null,
  "data_availability": "unavailable",
  "data_availability_reason": "no_price_history"
}
```

`price` is always the unadjusted close. `adjusted_close` is optional and does not replace the unadjusted Observed Price. Missing Listing returns `404` with `listing_not_found`.

### Get latest Financial Instrument price

```http
GET /instruments/{instrument_id}/prices/latest?status={status}
```

Convenience endpoint that uses the Financial Instrument's Preferred Listing and returns which Listing was used. By default, the endpoint may return a provisional or final price. If `status=final`, it returns the latest final price only.

Available response:

```json
{
  "instrument_id": "uuid",
  "listing_id": "uuid",
  "date": "2026-06-01",
  "price": 8.18,
  "price_kind": "close",
  "open": 8.10,
  "high": 8.20,
  "low": 8.05,
  "close": 8.18,
  "adjusted_close": 8.12,
  "volume": 1200000,
  "currency": "EUR",
  "status": "final",
  "data_availability": "available",
  "data_availability_reason": null,
  "data_provider": "yahoo",
  "retrieved_at": "2026-06-01T18:10:00Z"
}
```

Unavailable-data response when no Preferred Listing exists:

```json
{
  "instrument_id": "uuid",
  "listing_id": null,
  "price": null,
  "data_availability": "unavailable",
  "data_availability_reason": "no_preferred_listing"
}
```

Missing instrument returns `404` with `instrument_not_found`.

### Get Listing price history

```http
GET /listings/{listing_id}/prices/history?from={date}&to={date}&status={status}&limit={limit}
```

Returns daily Observed Prices for a Listing ordered by date ascending. `from` and `to` are optional; when both are omitted, v1 defaults to the latest 12 months to protect response size. Consumers should pass an explicit date range when they need longer history. `status=final` filters out provisional prices.

Response:

```json
{
  "listing_id": "uuid",
  "instrument_id": "uuid",
  "currency": "EUR",
  "data_availability": "available",
  "data_availability_reason": null,
  "items": [
    {
      "date": "2026-06-01",
      "open": 8.10,
      "high": 8.20,
      "low": 8.05,
      "close": 8.18,
      "adjusted_close": 8.12,
      "volume": 1200000,
      "status": "final",
      "data_provider": "yahoo",
      "retrieved_at": "2026-06-01T18:10:00Z"
    }
  ]
}
```

If the Listing exists but no prices are available for the requested range, the endpoint returns `200` with an empty `items` array and explicit Data Availability. Missing Listing returns `404` with `listing_not_found`.

### Get Financial Instrument price history

```http
GET /instruments/{instrument_id}/prices/history?from={date}&to={date}&status={status}&limit={limit}
```

Convenience endpoint that uses the Financial Instrument's Preferred Listing and returns daily Observed Prices ordered by date ascending. Date range defaults are the same as the Listing price history endpoint.

Response:

```json
{
  "instrument_id": "uuid",
  "listing_id": "uuid",
  "currency": "EUR",
  "data_availability": "available",
  "data_availability_reason": null,
  "items": [
    {
      "date": "2026-06-01",
      "open": 8.10,
      "high": 8.20,
      "low": 8.05,
      "close": 8.18,
      "adjusted_close": 8.12,
      "volume": 1200000,
      "status": "final",
      "data_provider": "yahoo",
      "retrieved_at": "2026-06-01T18:10:00Z"
    }
  ]
}
```

If no Preferred Listing exists, the endpoint returns `200` with `items: []`, `listing_id: null`, `data_availability: "unavailable"`, and `data_availability_reason: "no_preferred_listing"`. Missing instrument returns `404` with `instrument_not_found`.

### Get Financial Instrument dividends

```http
GET /instruments/{instrument_id}/dividends?from={date}&to={date}&limit={limit}
```

Returns cash Dividends associated with a Financial Instrument. `from` and `to` are optional; when omitted, v1 may return all locally stored dividends up to the configured response limit. Results are ordered by `ex_date` ascending when a range is supplied.

Response:

```json
{
  "instrument_id": "uuid",
  "currency_policy": "native",
  "data_availability": "available",
  "data_availability_reason": null,
  "items": [
    {
      "ex_date": "2026-05-15",
      "payment_date": "2026-05-22",
      "amount": 0.43,
      "currency": "EUR",
      "data_provider": "yahoo",
      "retrieved_at": "2026-05-16T08:00:00Z"
    }
  ]
}
```

Dividends are returned in their native currency. Missing instrument returns `404` with `instrument_not_found`.

### Get Financial Instrument corporate actions

```http
GET /instruments/{instrument_id}/corporate-actions?from={date}&to={date}&limit={limit}
```

Returns v1 Corporate Actions associated with a Financial Instrument: splits, reverse splits, and mergers. Results are ordered by `effective_date` ascending when a range is supplied.

Response:

```json
{
  "instrument_id": "uuid",
  "data_availability": "available",
  "data_availability_reason": null,
  "items": [
    {
      "action_type": "split",
      "effective_date": "2026-06-01",
      "ratio_numerator": 2,
      "ratio_denominator": 1,
      "target_instrument_id": null,
      "description": "2-for-1 split",
      "data_provider": "yahoo",
      "retrieved_at": "2026-06-02T08:00:00Z"
    }
  ]
}
```

Missing instrument returns `404` with `instrument_not_found`.

### Get Data Availability

```http
GET /instruments/{instrument_id}/data-availability
GET /listings/{listing_id}/data-availability
```

Returns granular Data Availability for a known Financial Instrument or Listing. This endpoint is useful when consumers need to explain stale or unavailable data by category.

Response:

```json
{
  "subject_type": "financial_instrument",
  "subject_id": "uuid",
  "items": [
    {
      "data_type": "metadata",
      "status": "available",
      "reason": null,
      "checked_at": "2026-06-01T08:00:00Z"
    },
    {
      "data_type": "latest_price",
      "status": "stale",
      "reason": "older_than_expected_market_day",
      "checked_at": "2026-06-01T08:00:00Z"
    }
  ]
}
```

Missing subject returns `404` with `instrument_not_found` or `listing_not_found`.

## Status code summary

- `200 OK`: successful synchronous read, including empty search results or unavailable data for an existing resource.
- `202 Accepted`: asynchronous Instrument Discovery Request created.
- `400 Bad Request`: invalid query parameters or malformed request body.
- `404 Not Found`: addressed canonical resource or exact lookup target does not exist locally.
- `409 Conflict`: exact lookup is ambiguous.
- `429 Too Many Requests`: Discovery Search or acquisition request is rate-limited.
- `503 Service Unavailable`: Discovery Search is disabled or provider discovery is temporarily unavailable.

## V1 exclusions

The v1 API does not expose FX conversion, realtime quotes, intraday bars, adjusted OHLCV calculated by the service, bond analytics, or direct provider pass-through data as canonical market data.
