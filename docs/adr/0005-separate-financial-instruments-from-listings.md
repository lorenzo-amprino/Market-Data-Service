# Separate Financial Instruments from Listings

Market Data Service will model Financial Instruments as canonical economic items and Listings as venue-specific occurrences of those instruments, each with its own venue, listing symbol, trading currency, lifecycle, market calendar, and observed price history. This deliberately rejects treating tickers or provider symbols as canonical identity, allowing one instrument to have multiple listings while keeping provider identifiers and venue-specific symbols as lookup data rather than source-of-truth identifiers.
