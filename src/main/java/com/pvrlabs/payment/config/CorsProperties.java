package com.pvrlabs.payment.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "app.cors")
public record CorsProperties(
        List<String> allowedOrigins,
        String allowedMethods,
        String allowedHeaders,
        boolean allowCredentials,
        long maxAge
) {
}
