package com.pvrlabs.payment.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "services")
public record DownstreamServicesProperties(
        ServiceEndpoint order,
        ServiceEndpoint product,
        ServiceEndpoint user
) {
    public record ServiceEndpoint(String baseUrl) {
    }
}
