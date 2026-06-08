# Use Java and Spring Boot for the application stack

Market Data Service will be implemented with Java 25 and Spring Boot as its primary application stack. Java is preferred over Python because the maintainer's strongest production experience is in Java, and Spring Boot is preferred over lower-footprint Java frameworks because the service needs mature support for HTTP APIs, PostgreSQL, scheduling, retries, observability, configuration, and integration testing; runtime footprint will be controlled through careful dependency selection, JVM/container tuning, and conservative pool sizing.
