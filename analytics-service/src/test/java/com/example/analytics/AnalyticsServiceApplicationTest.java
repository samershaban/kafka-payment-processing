package com.example.analytics;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class AnalyticsServiceApplicationTest {

    @Autowired TestRestTemplate http;

    @Test
    void startsAndReportsHealthy() {
        assertThat(http.getForObject("/actuator/health", String.class)).contains("\"status\":\"UP\"");
    }
}
