package com.pvrlabs.payment.config;

import com.cashfree.pg.Cashfree;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class CashfreeConfig {

    private final CashfreeProperties properties;

    @Bean
    public Cashfree cashfreeClient() {
        validateCredentials();

        Cashfree.CFEnvironment environment = resolveEnvironment(properties.environment());
        log.info("Initializing Cashfree client | environment={}", environment);

        // Secrets stay server-side only — never returned in API responses.
        return new Cashfree(
                environment,
                properties.clientId(),
                properties.clientSecret(),
                null,
                null,
                null
        );
    }

    private void validateCredentials() {
        if (!StringUtils.hasText(properties.clientId()) || !StringUtils.hasText(properties.clientSecret())) {
            throw new IllegalStateException(
                    "Cashfree credentials are missing. Set CASHFREE_CLIENT_ID and CASHFREE_CLIENT_SECRET."
            );
        }
    }

    private Cashfree.CFEnvironment resolveEnvironment(String value) {
        if (value != null && value.equalsIgnoreCase("PRODUCTION")) {
            return Cashfree.PRODUCTION;
        }
        return Cashfree.SANDBOX;
    }
}
