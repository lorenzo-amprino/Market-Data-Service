# Use internal UUID resource identifiers

Market Data Service canonical API paths will identify resources with internal UUIDs, while natural or external identifiers such as ISIN, venue code plus listing symbol, and provider identifiers will be resolved through lookup endpoints. This avoids making mutable, ambiguous, or provider-specific identifiers part of canonical resource identity, while still supporting exact lookup for consumers that start from market or provider identifiers.
