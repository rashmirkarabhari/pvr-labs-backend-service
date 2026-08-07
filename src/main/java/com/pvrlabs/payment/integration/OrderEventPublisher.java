package com.pvrlabs.payment.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.pvrlabs.payment.dto.request.CreateOrderRequestDto;

/**
 * Abstraction for publishing payment lifecycle events to Order / User services.
 * Current implementation is a no-op logger; replace with RestClient / messaging later.
 */
public interface OrderEventPublisher {

    void publishOrderCreated(String orderId, CreateOrderRequestDto request);

    void publishPaymentWebhook(String orderId, String eventType, String paymentStatus, JsonNode payload);
}
