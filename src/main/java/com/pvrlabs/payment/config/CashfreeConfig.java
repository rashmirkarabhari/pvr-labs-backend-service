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

    public static final String SANDBOX_BASE_URL = "https://sandbox.cashfree.com/pg";
    public static final String PRODUCTION_BASE_URL = "https://api.cashfree.com/pg";
    public static final String CREATE_ORDER_PATH = "/orders";

    private final CashfreeProperties properties;

    @Bean
    public Cashfree cashfreeClient() {
        validateCredentials();

        Cashfree.CFEnvironment environment = resolveEnvironment(properties.environment());
        String baseUrl = baseUrlFor(environment);

        // Secrets stay server-side only — never returned in API responses / logs.
        Cashfree client = new Cashfree(
                environment,
                properties.clientId().trim(),
                properties.clientSecret().trim(),
                null,
                null,
                null
        );

        if (StringUtils.hasText(properties.apiVersion())) {
            client.XApiVersion = properties.apiVersion().trim();
        }

        log.info(
                "Initializing Cashfree client | environment={} baseUrl={} createOrderEndpoint={} clientId={} apiVersion={} headers=[x-client-id,x-client-secret,x-api-version,Content-Type]",
                environment,
                baseUrl,
                baseUrl + CREATE_ORDER_PATH,
                maskClientId(properties.clientId()),
                client.XApiVersion
        );

        warnIfCredentialEnvironmentMismatch(environment, properties.clientId(), properties.clientSecret());

        return client;
    }

    public static String baseUrlFor(Cashfree.CFEnvironment environment) {
        return environment == Cashfree.PRODUCTION ? PRODUCTION_BASE_URL : SANDBOX_BASE_URL;
    }

    private void validateCredentials() {
        if (!StringUtils.hasText(properties.clientId()) || !StringUtils.hasText(properties.clientSecret())) {
            throw new IllegalStateException(
                    "Cashfree credentials are missing. Set CASHFREE_CLIENT_ID and CASHFREE_CLIENT_SECRET "
                            + "(use TEST environment keys from Cashfree Merchant Dashboard → Developers → API Keys)."
            );
        }
    }

    private Cashfree.CFEnvironment resolveEnvironment(String value) {
        if (value != null && value.equalsIgnoreCase("PRODUCTION")) {
            return Cashfree.PRODUCTION;
        }
        return Cashfree.SANDBOX;
    }

    /**
     * Detects common misconfigurations (Prod keys with Sandbox mode and vice versa).
     * Never logs the secret value.
     */
    private void warnIfCredentialEnvironmentMismatch(Cashfree.CFEnvironment environment,
                                                     String clientId,
                                                     String clientSecret) {
        String id = clientId == null ? "" : clientId.trim();
        String secret = clientSecret == null ? "" : clientSecret.trim().toLowerCase();

        boolean looksLikeTestId = id.regionMatches(true, 0, "TEST", 0, 4);
        boolean looksLikeProdId = id.regionMatches(true, 0, "PROD", 0, 4);
        boolean looksLikeTestSecret = secret.contains("_test_") || secret.startsWith("test");
        boolean looksLikeProdSecret = secret.contains("_prod_") || secret.startsWith("prod");

        if (environment == Cashfree.SANDBOX && (looksLikeProdId || looksLikeProdSecret)) {
            log.warn(
                    "Cashfree environment=SANDBOX but credentials look like PRODUCTION keys. "
                            + "Use TEST App ID / Secret from the Cashfree dashboard (Switch to Test)."
            );
        }
        if (environment == Cashfree.PRODUCTION && (looksLikeTestId || looksLikeTestSecret)) {
            log.warn(
                    "Cashfree environment=PRODUCTION but credentials look like TEST keys. "
                            + "Use PROD App ID / Secret from the Cashfree dashboard."
            );
        }
        if (environment == Cashfree.SANDBOX && !looksLikeTestId) {
            log.warn(
                    "Cashfree SANDBOX App ID usually starts with 'TEST'. Verify you copied Test API Keys "
                            + "from Merchants Dashboard while in Test Environment."
            );
        }
    }

    /** Masks Client ID for logs — never logs the Client Secret. */
    public static String maskClientId(String clientId) {
        if (!StringUtils.hasText(clientId)) {
            return "(missing)";
        }
        if (clientId.length() <= 8) {
            return "****";
        }
        return clientId.substring(0, 4) + "****" + clientId.substring(clientId.length() - 4);
    }
}
