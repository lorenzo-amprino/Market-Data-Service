create table data_provider (
  provider_code text primary key,
  name text not null,
  enabled boolean not null default true,
  created_at timestamptz not null,
  updated_at timestamptz not null
);

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
