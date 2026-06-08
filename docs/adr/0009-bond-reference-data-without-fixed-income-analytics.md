# Provide bond reference data without fixed-income analytics in v1

Market Data Service v1 will store basic Bond Metadata and daily observed prices for bond Financial Instruments when provider data is available, but it will not calculate accrued interest, yield, duration, or clean/dirty price conversions. This keeps the service responsible for market data and reference facts rather than fixed-income analytics, which can be introduced later as a separate capability or delegated to a consumer service.
