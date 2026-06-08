# Use PostgreSQL as the primary market data store

Market Data Service will use PostgreSQL as its primary database and source of truth for canonical instrument identity, listings, market reference data, and daily observed price history. PostgreSQL is preferred over MongoDB or another schemaless primary store because the domain has strong relational identity and consistency needs, while JSONB remains available for flexible provider-specific or instrument-type metadata; schemaless storage may be revisited later for raw provider responses or ingestion staging, but not as the v1 source of truth.
