package com.pvrlabs.payment.config;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Confirms Cashfree credentials are present without exposing secret values.
 */
@Component
public class CashfreeHealthIndicator implements HealthIndicator {

    private final CashfreeProperties properties;

    public CashfreeHealthIndicator(CashfreeProperties properties) {
        this.properties = properties;
    }

    @Override
    public Health health() {
        boolean configured = StringUtils.hasText(properties.clientId())
                && StringUtils.hasText(properties.clientSecret());

        if (!configured) {
            return Health.down()
                    .withDetail("cashfree", "Credentials missing")
                    .build();
        }

        return Health.up()
                .withDetail("cashfree", "Configured")
                .withDetail("environment", properties.environment())
                .withDetail("clientId", CashfreeConfig.maskClientId(properties.clientId()))
                .build();
    }
}
