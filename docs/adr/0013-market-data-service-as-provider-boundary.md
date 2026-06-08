# Use Market Data Service as the provider boundary

Market Data Service will be the platform boundary for external market Data Providers: application services must consume market reference data, observed prices, dividends, and corporate actions through Market Data Service APIs instead of querying Yahoo Finance, Alpha Vantage, Polygon, FMP, or similar providers directly. This centralizes provider integration, normalization, provenance, rate-limit handling, and licensing controls, preventing divergent market data semantics across the platform.
