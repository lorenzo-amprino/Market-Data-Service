# Use bounded default backfills

Market Data Service ingestion will use bounded default backfills when a Financial Instrument or Listing enters the Universe, rather than automatically retrieving all historical data available from Data Providers. This protects v1 ingestion from provider rate limits, long-running jobs, unpredictable costs, and recovery complexity, while allowing longer or full-history recovery through explicit admin backfill workflows when needed.
