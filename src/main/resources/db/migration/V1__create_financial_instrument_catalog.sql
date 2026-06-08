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

create index financial_instrument_search_idx
  on financial_instrument using gin (
    to_tsvector('simple', coalesce(name, '') || ' ' || coalesce(isin, '') || ' ' || coalesce(issuer, ''))
  );

create table venue (
  venue_code text primary key,
  name text not null,
  country text null,
  timezone text not null,
  calendar_code text not null,
  created_at timestamptz not null,
  updated_at timestamptz not null
);

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

create index listing_symbol_search_idx
  on listing using gin (to_tsvector('simple', symbol));
