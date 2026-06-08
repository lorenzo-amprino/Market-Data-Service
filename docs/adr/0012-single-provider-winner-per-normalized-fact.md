# Use a single provider winner per normalized fact

Market Data Service v1 will select one Data Provider as the source for each normalized market data fact according to a configured priority and fallback order, which may vary by data type and item when needed. If the preferred provider fails or does not return a specific item, ingestion may try the next provider, but v1 will not merge conflicting values from multiple providers; this avoids reconciliation complexity while relying on Data Provenance to make the winning source explicit.
