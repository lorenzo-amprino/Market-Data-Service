package com.lamprino.marketdata;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.datasource.url=jdbc:postgresql://localhost:${test.unused-postgres-port}/market_data",
                "spring.datasource.username=market_data",
                "spring.datasource.password=market_data",
                "spring.datasource.hikari.initialization-fail-timeout=0",
                "spring.datasource.hikari.connection-timeout=250",
                "spring.datasource.hikari.validation-timeout=250",
                "spring.flyway.enabled=false",
                "spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect",
                "spring.jpa.hibernate.ddl-auto=none"
        })
class MarketDataUnavailablePostgresTests {

    static {
        System.setProperty("test.unused-postgres-port", String.valueOf(findUnusedTcpPort()));
    }

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void readinessIsDownWhenPostgreSqlIsUnavailable() {
        ResponseEntity<Map> readiness = restTemplate.getForEntity("/actuator/health/readiness", Map.class);

        assertThat(readiness.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(readiness.getBody()).containsEntry("status", "DOWN");
    }

    private static int findUnusedTcpPort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to find an unused TCP port for PostgreSQL failure test", ex);
        }
    }
}
