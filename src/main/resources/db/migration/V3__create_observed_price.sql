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
