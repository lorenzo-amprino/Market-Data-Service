# Market Data Service Database Design

## Purpose

This document describes the v1 PostgreSQL schema design for Market Data Service. PostgreSQL is the source of truth for canonical Financial Instruments, Listings, market reference data, daily Observed Prices, provider provenance, Universe membership, and discovery/acquisition state.

## Design constraints from ADRs

- PostgreSQL is the primary market data store.
- Financial Instruments and Listings are separate concepts.
- Canonical API resources use internal UUIDs.
- Canonical read APIs serve local database data.
- Discovery Search candidates are not canonical records until acquired.
- Daily Observed Prices are stored per Listing.
- FX conversion and fixed-income analytics are outside v1.
- Ingestion consistency is per Financial Instrument aggregate or Listing price series aggregate.
- Universe membership is distinct from Data Availability.
- A normalized fact has one winning Data Provider.
- Default backfills are bounded.

## Identifier strategy

The schema uses a mixed identifier strategy:

- Canonical entities and facts use UUID primary keys, especially when they are exposed as API resources or represent lifecycle-bearing records.
- Stable reference tables use natural code primary keys when the code is the domain identity.

Examples:

- `financial_instrument.id uuid primary key`
- `listing.id uuid primary key`
- `dividend.id uuid primary key`
- `corporate_action.id uuid primary key`
- `instrument_discovery_request.id uuid primary key`
- `venue.venue_code text primary key`
- `currency.code text primary key`, if a currency table is introduced
- `data_provider.code text primary key`, if a data provider table is introduced

## Core tables

### `financial_instrument`

Stores canonical Financial Instrument identity, common issuer information when applicable, lifecycle state, Data Availability, and Universe membership for instrument-level ingestion.

```sql
create table financial_instrument (
  id uuid primary key,
  instrument_type text not null,
  name text not null,
  isin text null,
  issuer text null,
  issuer_country text null,
  status text not null,
  data_availability text not null,
  data_availability_reason text null,
  universe_member boolean not null default false,
  sync_enabled boolean not null default true,
  created_at timestamptz not null,
  updated_at timestamptz not null,

  constraint financial_instrument_type_check
    check (instrument_type in ('stock', 'etf', 'bond')),
  constraint financial_instrument_status_check
    check (status in ('active', 'inactive', 'matured', 'merged')),
  constraint financial_instrument_data_availability_check
    check (data_availability in ('available', 'stale', 'unavailable'))
);

create unique index financial_instrument_isin_unique
  on financial_instrument (isin)
  where isin is not null;
```

Instrument-type-specific metadata lives in separate tables, not in `financial_instrument`. `issuer` and `issuer_country` are nullable and apply primarily to stock and bond instruments; ETF manager, domicile country, and index provider live in `etf_metadata`.

### `venue`

Stores trading venues used by Listings. `venue_code` is the stable venue identity used by the domain, such as `XMIL` or `XETR`.

```sql
create table venue (
  venue_code text primary key,
  name text not null,
  country text null,
  timezone text not null,
  calendar_code text not null,
  created_at timestamptz not null,
  updated_at timestamptz not null
);
```

`calendar_code` may match `venue_code`, but remains separate so multiple venues can share a Market Calendar or a venue can use a specialized calendar mapping.

### `listing`

Stores venue-specific occurrences of Financial Instruments. Listing symbols are unique only within a Venue.

```sql
create table listing (
  id uuid primary key,
  financial_instrument_id uuid not null references financial_instrument (id),
  venue_code text not null references venue (venue_code),
  symbol text not null,
  currency_code text not null,
  status text not null,
  preferred boolean not null default false,
  data_availability text not null,
  data_availability_reason text null,
  universe_member boolean not null default false,
  sync_enabled boolean not null default true,
  created_at timestamptz not null,
  updated_at timestamptz not null,

  constraint listing_status_check
    check (status in ('active', 'suspended', 'delisted')),
  constraint listing_data_availability_check
    check (data_availability in ('available', 'stale', 'unavailable')),
  constraint listing_venue_symbol_unique
    unique (venue_code, symbol)
);

create unique index listing_one_preferred_per_instrument
  on listing (financial_instrument_id)
  where preferred = true;

create index listing_financial_instrument_id_idx
  on listing (financial_instrument_id);
```

`currency_code` is stored as text in v1; a `currency` reference table can be introduced later if needed.

### `data_provider`

Stores Data Providers known by the service. Provider-specific runtime settings such as credentials, rate limits, and endpoint URLs should live in configuration, not necessarily in this table.

```sql
create table data_provider (
  provider_code text primary key,
  name text not null,
  enabled boolean not null default true,
  created_at timestamptz not null,
  updated_at timestamptz not null
);
```

### `provider_identifier`

Maps Data Provider-specific identifiers to canonical Financial Instruments or Listings. Discovery Search candidates are not stored here until they are acquired as canonical records.

```sql
create table provider_identifier (
  id uuid primary key,
  provider_code text not null references data_provider (provider_code),
  provider_identifier text not null,
  financial_instrument_id uuid null references financial_instrument (id),
  listing_id uuid null references listing (id),
  created_at timestamptz not null,
  updated_at timestamptz not null,

  constraint provider_identifier_unique
    unique (provider_code, provider_identifier),
  constraint provider_identifier_one_target_check
    check (
      (financial_instrument_id is not null and listing_id is null)
      or
      (financial_instrument_id is null and listing_id is not null)
    )
);

create index provider_identifier_financial_instrument_id_idx
  on provider_identifier (financial_instrument_id)
  where financial_instrument_id is not null;

create index provider_identifier_listing_id_idx
  on provider_identifier (listing_id)
  where listing_id is not null;
```

### `observed_price`

Stores daily unadjusted OHLCV Observed Prices for a single Listing. The natural key is one price record per Listing and price date.

```sql
create table observed_price (
  listing_id uuid not null references listing (id),
  price_date date not null,
  open numeric(20, 8) null,
  high numeric(20, 8) null,
  low numeric(20, 8) null,
  close numeric(20, 8) not null,
  adjusted_close numeric(20, 8) null,
  volume numeric(30, 0) null,
  status text not null,
  data_provider_code text not null references data_provider (provider_code),
  retrieved_at timestamptz not null,
  created_at timestamptz not null,
  updated_at timestamptz not null,

  primary key (listing_id, price_date),

  constraint observed_price_status_check
    check (status in ('provisional', 'final')),
  constraint observed_price_open_non_negative_check
    check (open is null or open >= 0),
  constraint observed_price_high_non_negative_check
    check (high is null or high >= 0),
  constraint observed_price_low_non_negative_check
    check (low is null or low >= 0),
  constraint observed_price_close_non_negative_check
    check (close >= 0),
  constraint observed_price_adjusted_close_non_negative_check
    check (adjusted_close is null or adjusted_close >= 0),
  constraint observed_price_volume_non_negative_check
    check (volume is null or volume >= 0)
);

create index observed_price_listing_latest_idx
  on observed_price (listing_id, price_date desc);

create index observed_price_retrieved_at_idx
  on observed_price (retrieved_at);
```

`observed_price` uses a composite primary key instead of a UUID because ingestion needs idempotent upserts by Listing and date, and price history is queried as a Listing time series.

Ingestion upserts may update a provisional record to final for the same `(listing_id, price_date)`. A final record should not normally become provisional again, but provider corrections may update final price values while keeping `status = 'final'` and refreshing `retrieved_at`.

Latest price endpoints query `observed_price` directly using `(listing_id, price_date desc)` in v1. No separate `latest_price` table is required initially.

### `dividend`

Stores cash distributions associated with Financial Instruments.

```sql
create table dividend (
  id uuid primary key,
  financial_instrument_id uuid not null references financial_instrument (id),
  ex_date date not null,
  payment_date date null,
  amount numeric(20, 8) not null,
  currency_code text not null,
  data_provider_code text not null references data_provider (provider_code),
  retrieved_at timestamptz not null,
  created_at timestamptz not null,
  updated_at timestamptz not null,

  constraint dividend_amount_non_negative_check
    check (amount >= 0),
  constraint dividend_unique
    unique (financial_instrument_id, ex_date, amount, currency_code)
);

create index dividend_financial_instrument_ex_date_idx
  on dividend (financial_instrument_id, ex_date desc);
```

### `corporate_action`

Stores v1 Corporate Actions associated with Financial Instruments.

```sql
create table corporate_action (
  id uuid primary key,
  financial_instrument_id uuid not null references financial_instrument (id),
  action_type text not null,
  effective_date date not null,
  ratio_numerator numeric(30, 10) null,
  ratio_denominator numeric(30, 10) null,
  target_financial_instrument_id uuid null references financial_instrument (id),
  description text null,
  data_provider_code text not null references data_provider (provider_code),
  retrieved_at timestamptz not null,
  created_at timestamptz not null,
  updated_at timestamptz not null,

  constraint corporate_action_type_check
    check (action_type in ('split', 'reverse_split', 'merger')),
  constraint corporate_action_ratio_positive_check
    check (
      (ratio_numerator is null or ratio_numerator > 0)
      and
      (ratio_denominator is null or ratio_denominator > 0)
    ),
  constraint corporate_action_split_ratio_check
    check (
      action_type not in ('split', 'reverse_split')
      or
      (ratio_numerator is not null and ratio_denominator is not null)
    ),
  constraint corporate_action_unique
    unique (financial_instrument_id, action_type, effective_date)
);

create index corporate_action_financial_instrument_effective_date_idx
  on corporate_action (financial_instrument_id, effective_date desc);
```

For mergers, `target_financial_instrument_id` is nullable because the successor instrument may not be known or acquired when the action is first recorded.

### `etf_metadata`

Stores ETF-specific reference metadata as an optional one-to-one extension of `financial_instrument`.

```sql
create table etf_metadata (
  financial_instrument_id uuid primary key references financial_instrument (id),
  fund_manager text null,
  domicile_country text null,
  ter numeric(10, 6) null,
  replication text null,
  distribution_policy text null,
  benchmark text null,
  index_provider text null,
  data_provider_code text null references data_provider (provider_code),
  retrieved_at timestamptz null,
  created_at timestamptz not null,
  updated_at timestamptz not null,

  constraint etf_metadata_ter_non_negative_check
    check (ter is null or ter >= 0)
);
```

The `financial_instrument_id` should reference an instrument with `instrument_type = 'etf'`; this is enforced by application logic in v1 rather than a database trigger.

### `bond_metadata`

Stores bond-specific reference metadata as an optional one-to-one extension of `financial_instrument`. Bond analytics such as accrued interest, yield, duration, and clean/dirty price conversion are not stored here in v1.

```sql
create table bond_metadata (
  financial_instrument_id uuid primary key references financial_instrument (id),
  maturity_date date null,
  coupon numeric(12, 6) null,
  coupon_frequency text null,
  currency_code text null,
  bond_type text null,
  data_provider_code text null references data_provider (provider_code),
  retrieved_at timestamptz null,
  created_at timestamptz not null,
  updated_at timestamptz not null,

  constraint bond_metadata_coupon_non_negative_check
    check (coupon is null or coupon >= 0)
);
```

The `financial_instrument_id` should reference an instrument with `instrument_type = 'bond'`; this is enforced by application logic in v1 rather than a database trigger. `bond_type` and `coupon_frequency` remain nullable text fields in v1 because provider classifications vary and several categories can overlap.

### `instrument_discovery_request`

Tracks asynchronous acquisition requests for candidate instruments or listings selected through Discovery Search or supplied directly by an admin, user, or portfolio-driven workflow. Discovery Search candidates themselves are not persisted in v1 unless acquisition is requested.

```sql
create table instrument_discovery_request (
  id uuid primary key,
  status text not null,
  query text null,
  provider_code text null references data_provider (provider_code),
  provider_identifier text null,
  requested_by text null,
  add_to_universe boolean not null default true,
  result_financial_instrument_id uuid null references financial_instrument (id),
  result_listing_id uuid null references listing (id),
  failure_reason text null,
  created_at timestamptz not null,
  updated_at timestamptz not null,
  completed_at timestamptz null,

  constraint instrument_discovery_request_status_check
    check (status in ('pending', 'running', 'completed', 'failed', 'cancelled'))
);

create index instrument_discovery_request_status_created_at_idx
  on instrument_discovery_request (status, created_at);
```

### `provider_raw_response`

Optionally stores temporary raw Data Provider responses as ingestion evidence for debugging and reprocessing. Raw responses are not canonical Market Data Service facts and are subject to retention cleanup and provider license constraints.

```sql
create table provider_raw_response (
  id uuid primary key,
  provider_code text not null references data_provider (provider_code),
  purpose text not null,
  request_key text null,
  retrieved_at timestamptz not null,
  expires_at timestamptz not null,
  payload jsonb not null,
  created_at timestamptz not null
);

create index provider_raw_response_expires_at_idx
  on provider_raw_response (expires_at);

create index provider_raw_response_provider_retrieved_at_idx
  on provider_raw_response (provider_code, retrieved_at desc);
```

### `data_availability_status`

Stores granular Data Availability by subject and data type. The summary `data_availability` fields on `financial_instrument` and `listing` remain useful for simple API responses, while this table captures whether specific data categories are available, stale, or unavailable.

```sql
create table data_availability_status (
  subject_type text not null,
  subject_id uuid not null,
  data_type text not null,
  status text not null,
  reason text null,
  checked_at timestamptz not null,
  updated_at timestamptz not null,

  primary key (subject_type, subject_id, data_type),

  constraint data_availability_status_subject_type_check
    check (subject_type in ('financial_instrument', 'listing')),
  constraint data_availability_status_data_type_check
    check (data_type in ('metadata', 'latest_price', 'price_history', 'dividends', 'corporate_actions')),
  constraint data_availability_status_status_check
    check (status in ('available', 'stale', 'unavailable'))
);

create index data_availability_status_status_idx
  on data_availability_status (status);
```

PostgreSQL cannot enforce a polymorphic foreign key from `(subject_type, subject_id)` to both `financial_instrument` and `listing`; application logic enforces subject validity in v1.

## Deferred tables

V1 does not introduce `ingestion_job` or `ingestion_job_item` tables. Ingestion can start with Spring scheduling, structured logs, per-run retry/backoff, and simple checkpointing only where needed. Persistent job and item tracking can be added later if operational needs require resumable multi-item workflows, dashboarding, or manual retry management.

V1 does not introduce market calendar tables. Market Calendars are resolved by application code from a library or static dataset using `venue.calendar_code`; database tables for holidays or manual overrides can be added later when operationally needed.

## Schema sections

_To be filled as design decisions are resolved._
