# Use Market Calendars for price freshness

Market Data Service v1 will evaluate latest-price freshness using the Market Calendar of the relevant Listing rather than a simple elapsed-time rule. This prevents weekends, holidays, and special trading closures from being treated as missing data, while allowing static calendar datasets or libraries with manual overrides for exceptional trading days.
