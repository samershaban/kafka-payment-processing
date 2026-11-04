package com.example.analytics.cdc;

import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Status;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CdcHealthIndicatorTest {

    private Status statusFor(UserCdcCapture.State state, String error) {
        UserCdcCapture capture = mock(UserCdcCapture.class);
        when(capture.status()).thenReturn(new UserCdcCapture.Status(state, 0, null, error));
        return new CdcHealthIndicator(capture).health().getStatus();
    }

    @Test
    void running_isUp() {
        assertThat(statusFor(UserCdcCapture.State.RUNNING, null)).isEqualTo(Status.UP);
    }

    @Test
    void failed_isDown_withTheReason() {
        UserCdcCapture capture = mock(UserCdcCapture.class);
        when(capture.status()).thenReturn(new UserCdcCapture.Status(
                UserCdcCapture.State.FAILED, 0, null, "Creation of replication slot failed"));

        var health = new CdcHealthIndicator(capture).health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsEntry("error", "Creation of replication slot failed");
    }

    @Test
    void disabled_isOutOfService() {
        assertThat(statusFor(UserCdcCapture.State.DISABLED, null)).isEqualTo(Status.OUT_OF_SERVICE);
    }
}
