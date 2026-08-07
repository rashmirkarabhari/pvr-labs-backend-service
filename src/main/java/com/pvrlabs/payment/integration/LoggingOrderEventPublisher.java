package com.pvrlabs.payment.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.pvrlabs.payment.config.DownstreamServicesProperties;
import com.pvrlabs.payment.dto.request.CreateOrderRequestDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Placeholder publisher prepared for future Order / Product / User microservice wiring.
 * Logs intended outbound calls so integration points are obvious without coupling yet.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LoggingOrderEventPublisher implements OrderEventPublisher {

    private final DownstreamServicesProperties services;

    @Override
    public void publishOrderCreated(String orderId, CreateOrderRequestDto request) {
        log.debug("Future: POST {}/api/orders/payment-initiated orderId={} cartId={} userId={}",
                services.order().baseUrl(),
                orderId,
                request.getCartId(),
                request.getUserId());
    }

    @Override
    public void publishPaymentWebhook(String orderId, String eventType, String paymentStatus, JsonNode payload) {
        log.info("Future: notify Order service at {} | orderId={} eventType={} paymentStatus={}",
                services.order().baseUrl(), orderId, eventType, paymentStatus);
        log.debug("Future: notify User service at {} for payment notifications", services.user().baseUrl());
        log.debug("Future: optionally refresh Product inventory via {}", services.product().baseUrl());
    }
}
