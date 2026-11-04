package com.example.analytics;

import com.example.analytics.repository.OrderReadRepository;
import com.example.analytics.repository.OrderScanner;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class AnalyticsServiceApplicationTest {

    @Autowired TestRestTemplate http;
    // Cassandra is excluded in the test profile, so its beans are mocked
    @MockitoBean OrderReadRepository orders;
    @MockitoBean OrderScanner scanner;

    @Test
    void startsAndReportsHealthy() {
        assertThat(http.getForObject("/actuator/health", String.class)).contains("\"status\":\"UP\"");
    }
}
