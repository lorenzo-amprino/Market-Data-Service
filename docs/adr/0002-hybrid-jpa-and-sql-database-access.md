# Use JPA with explicit SQL for critical database paths

Market Data Service will use Spring Data JPA/Hibernate as the primary persistence approach, with explicit SQL through Spring JDBC or native queries for ingestion batch paths, upserts, and performance-sensitive observed price queries. This keeps ordinary reference-data persistence familiar while avoiding forcing high-volume historical price ingestion and time-series access patterns through an ORM abstraction.
