package com.pvrlabs.payment.service;

import com.pvrlabs.payment.dto.request.CreateOrderRequestDto;
import com.pvrlabs.payment.dto.response.CreateOrderResponseDto;
import com.pvrlabs.payment.dto.response.PaymentStatusResponseDto;
import com.pvrlabs.payment.dto.response.WebhookAckResponseDto;

public interface PaymentService {

    CreateOrderResponseDto createOrder(CreateOrderRequestDto request);

    PaymentStatusResponseDto getPaymentStatus(String orderId);

    PaymentStatusResponseDto verifyPayment(String orderId, String paymentId);

    WebhookAckResponseDto handleWebhook(String rawBody, String signature, String timestamp);
}
