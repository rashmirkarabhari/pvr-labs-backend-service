package com.pvrlabs.payment.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "cashfree")
public record CashfreeProperties(
        String clientId,
        String clientSecret,
        String environment,
        String apiVersion,
        String returnUrl,
        String notifyUrl
) {
}
