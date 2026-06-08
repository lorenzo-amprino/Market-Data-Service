# Market Data Service

The Market Data Service provides canonical market reference data and historical market information for the rest of the platform.

## Language

**Financial Instrument**:
An economic market-related item tracked by the platform, such as a stock, ETF, or bond. A Financial Instrument may have multiple Listings; cash, indexes, and non-ETF funds are not Financial Instruments in v1.
_Avoid_: Security, titolo, asset

**ISIN**:
A standard identifier for a Financial Instrument when available. In this service, ISIN belongs to the Financial Instrument, not the Listing, and is unique when present.
_Avoid_: Listing identifier, required identifier

**Currency**:
The monetary unit in which a Listing price, Dividend, or bond coupon is denominated. Currency is not a Financial Instrument in v1.
_Avoid_: Cash, currency instrument, FX rate

**Listing**:
A venue-specific occurrence of a Financial Instrument, identified by the Venue and the Listing Symbol used on that Venue. A Listing has its own lifecycle status, such as active, suspended, or delisted, and follows a Market Calendar.
_Avoid_: Ticker as identity, provider symbol, exchange-specific security

**Listing Symbol**:
The symbol used for a Listing on a specific Venue. A Listing Symbol is only meaningful together with its Venue.
_Avoid_: Ticker, provider symbol, canonical ID

**Venue**:
A trading place where a Financial Instrument may have a Listing, identified by a venue code such as XMIL or XETR.
_Avoid_: Exchange, market

**Observed Price**:
A daily unadjusted open-high-low-close-volume record for a Listing. Observed Prices are specific to the Listing's venue, symbol, and trading currency, and may be Provisional or Final.
_Avoid_: Instrument price, generic price, intraday bar, adjusted price

**Adjusted Close**:
A provider-supplied closing price adjusted for corporate actions or distributions. Adjusted Close is optional in v1 and does not replace the unadjusted Observed Price.
_Avoid_: Adjusted OHLC, normalized price

**Provisional Price**:
An Observed Price for a trading day that is still in progress or not yet settled by the data provider.
_Avoid_: Live price, real-time price

**Final Price**:
An Observed Price for a completed trading day that is no longer expected to change under normal conditions.
_Avoid_: Settled quote, official quote

**Latest Price**:
The freshest available Observed Price for a Listing. It may be Provisional during an active trading day or Final when no newer provisional price is available.
_Avoid_: Real-time price, live quote

**Preferred Listing**:
The Listing selected by Market Data as the default source when a consumer asks for Financial Instrument-level market data. A Preferred Listing is not necessarily the Listing held in a portfolio.
_Avoid_: Primary security, default ticker

**Dividend**:
A cash distribution associated with a Financial Instrument. Dividends are facts about the Financial Instrument, not about a specific Listing.
_Avoid_: Listing dividend, payout as price data

**Corporate Action**:
An event that affects a Financial Instrument's identity, quantity basis, or lifecycle. In v1, Corporate Actions are limited to splits, reverse splits, and mergers.
_Avoid_: Listing event, generic provider event

**Bond Metadata**:
Reference information about a bond Financial Instrument, such as issuer, maturity, coupon, coupon frequency, currency, and bond type. Bond Metadata does not include bond analytics such as accrued interest, yield, duration, or clean/dirty price calculations.
_Avoid_: Bond analytics, fixed-income calculations

**Issuer**:
The organization or government that issues a stock or bond Financial Instrument.
_Avoid_: Fund manager, index provider

**Issuer Country**:
The country of the Issuer for a stock or bond Financial Instrument.
_Avoid_: Country, venue country, domicile country

**Fund Manager**:
The organization responsible for managing an ETF Financial Instrument.
_Avoid_: Issuer, index provider

**Domicile Country**:
The legal domicile country of an ETF Financial Instrument.
_Avoid_: Country, issuer country, venue country

**Index Provider**:
The organization responsible for maintaining an index or benchmark.
_Avoid_: Issuer, fund manager, data provider

**Data Provider**:
An external source from which the Market Data Service retrieves market data, reference data, dividends, or corporate actions.
_Avoid_: Index provider, vendor, source

**Data Provenance**:
The origin of a normalized market data fact, including the Data Provider and retrieval time.
_Avoid_: Raw response, audit log

**Provider Identifier**:
A Data Provider-specific identifier or symbol used to refer to a Financial Instrument or Listing. A Provider Identifier is not canonical identity.
_Avoid_: Canonical ID, ticker, symbol

**Data Availability**:
The service's ability to provide usable data for a Financial Instrument or Listing. Its main states are available, stale, and unavailable; a reason may explain the state.
_Avoid_: Sync flag, active status

**Market Calendar**:
The trading schedule for a venue, including trading days and holidays. Market Calendars are used to determine whether missing or old price data is stale or expected.
_Avoid_: Weekday approximation, provider freshness

**Financial Instrument Status**:
The lifecycle state of a Financial Instrument, such as active, inactive, matured, or merged.
_Avoid_: Active boolean, listing status

**Universe**:
The set of Financial Instruments and Listings that the Market Data Service is configured to maintain through ingestion jobs.
_Avoid_: Watchlist, portfolio, catalog

**Discovery Search**:
A best-effort search against Data Providers for candidate Financial Instruments or Listings that are not yet known locally. Discovery Search results are candidates, not canonical Market Data Service records.
_Avoid_: Search, lookup, canonical search

**Instrument Discovery Request**:
A request to acquire a Financial Instrument or Listing that is not yet known by the Market Data Service.
_Avoid_: Search, lookup, global catalog import
