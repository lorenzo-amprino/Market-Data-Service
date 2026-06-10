create table currency (
  currency_code text primary key,

  constraint currency_code_format_check
    check (currency_code ~ '^[A-Z]{3}$')
);

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
    check (volume is null or volume >= 0),
  constraint observed_price_ohlc_bounds_check
    check (
      (low is null or open is null or low <= open)
      and (high is null or open is null or high >= open)
      and (low is null or low <= close)
      and (high is null or high >= close)
      and (low is null or high is null or low <= high)
    )
);

create index observed_price_listing_latest_idx
  on observed_price (listing_id, price_date desc);

create index observed_price_retrieved_at_idx
  on observed_price (retrieved_at);

create table dividend (
  id uuid primary key,
  financial_instrument_id uuid not null references financial_instrument (id),
  ex_date date not null,
  payment_date date null,
  amount numeric(20, 8) not null,
  currency_code text not null references currency (currency_code),
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
  constraint corporate_action_target_check
    check (
      (action_type = 'merger' and target_financial_instrument_id is not null)
      or
      (action_type <> 'merger' and target_financial_instrument_id is null)
    ),
  constraint corporate_action_unique
    unique (financial_instrument_id, action_type, effective_date)
);

create index corporate_action_financial_instrument_effective_date_idx
  on corporate_action (financial_instrument_id, effective_date desc);

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

create table bond_metadata (
  financial_instrument_id uuid primary key references financial_instrument (id),
  maturity_date date null,
  coupon numeric(12, 6) null,
  coupon_frequency text null,
  currency_code text null references currency (currency_code),
  bond_type text null,
  data_provider_code text null references data_provider (provider_code),
  retrieved_at timestamptz null,
  created_at timestamptz not null,
  updated_at timestamptz not null,

  constraint bond_metadata_coupon_non_negative_check
    check (coupon is null or coupon >= 0)
);

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

create table data_availability_status (
  financial_instrument_id uuid null references financial_instrument (id),
  listing_id uuid null references listing (id),
  data_type text not null,
  status text not null,
  reason text null,
  checked_at timestamptz not null,
  updated_at timestamptz not null,

  constraint data_availability_status_one_subject_check
    check (
      (financial_instrument_id is not null and listing_id is null)
      or
      (financial_instrument_id is null and listing_id is not null)
    ),
  constraint data_availability_status_data_type_check
    check (data_type in ('metadata', 'latest_price', 'price_history', 'dividends', 'corporate_actions')),
  constraint data_availability_status_subject_data_type_check
    check (
      (financial_instrument_id is not null and data_type in ('metadata', 'dividends', 'corporate_actions'))
      or
      (listing_id is not null and data_type in ('latest_price', 'price_history'))
    ),
  constraint data_availability_status_status_check
    check (status in ('available', 'stale', 'unavailable'))
);

create unique index data_availability_status_financial_instrument_unique
  on data_availability_status (financial_instrument_id, data_type)
  where financial_instrument_id is not null;

create unique index data_availability_status_listing_unique
  on data_availability_status (listing_id, data_type)
  where listing_id is not null;

create index data_availability_status_status_idx
  on data_availability_status (status);
