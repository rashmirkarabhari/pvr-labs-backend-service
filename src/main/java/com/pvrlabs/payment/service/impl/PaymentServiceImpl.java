package com.pvrlabs.payment.service.impl;

import com.cashfree.pg.ApiException;
import com.cashfree.pg.ApiResponse;
import com.cashfree.pg.Cashfree;
import com.cashfree.pg.model.CreateOrderRequest;
import com.cashfree.pg.model.CustomerDetails;
import com.cashfree.pg.model.OrderEntity;
import com.cashfree.pg.model.OrderMeta;
import com.cashfree.pg.model.PaymentEntity;
import com.fasterxml.jackson.databind.JsonNode;
import com.pvrlabs.payment.config.CashfreeProperties;
import com.pvrlabs.payment.dto.request.CreateOrderRequestDto;
import com.pvrlabs.payment.dto.request.CustomerDetailsDto;
import com.pvrlabs.payment.dto.response.CreateOrderResponseDto;
import com.pvrlabs.payment.dto.response.PaymentStatusResponseDto;
import com.pvrlabs.payment.dto.response.WebhookAckResponseDto;
import com.pvrlabs.payment.exception.PaymentException;
import com.pvrlabs.payment.exception.ResourceNotFoundException;
import com.pvrlabs.payment.exception.WebhookVerificationException;
import com.pvrlabs.payment.integration.OrderEventPublisher;
import com.pvrlabs.payment.service.PaymentService;
import com.pvrlabs.payment.util.JsonNodeUtil;
import com.pvrlabs.payment.util.OrderIdGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    private final Cashfree cashfree;
    private final CashfreeProperties cashfreeProperties;
    private final JsonNodeUtil jsonNodeUtil;
    private final OrderEventPublisher orderEventPublisher;

    @Override
    public CreateOrderResponseDto createOrder(CreateOrderRequestDto request) {
        String orderId = StringUtils.hasText(request.getOrderId())
                ? request.getOrderId()
                : OrderIdGenerator.generate();

        log.info("Creating Cashfree order | orderId={} amount={} {}",
                orderId, request.getOrderAmount(), request.getOrderCurrency());

        try {
            CreateOrderRequest cfRequest = buildCreateOrderRequest(request, orderId);
            ApiResponse<OrderEntity> response = cashfree.PGCreateOrder(cfRequest, null, null, null);

            OrderEntity order = response.getData();
            if (order == null) {
                throw new PaymentException("Cashfree returned an empty order response");
            }

            log.info("Cashfree order created | orderId={} status={} paymentSessionIdPresent={}",
                    order.getOrderId(),
                    order.getOrderStatus(),
                    StringUtils.hasText(order.getPaymentSessionId()));

            // Hook for future Order service sync — no-op until wired.
            orderEventPublisher.publishOrderCreated(order.getOrderId(), request);

            return CreateOrderResponseDto.builder()
                    .orderId(order.getOrderId())
                    .paymentSessionId(order.getPaymentSessionId())
                    .orderAmount(order.getOrderAmount())
                    .orderCurrency(order.getOrderCurrency())
                    .orderStatus(order.getOrderStatus())
                    .environment(normalizeEnvironment(cashfreeProperties.environment()))
                    .build();
        } catch (ApiException ex) {
            log.error("Cashfree create-order failed | orderId={} code={} body={}",
                    orderId, ex.getCode(), ex.getResponseBody());
            throw new PaymentException(
                    "Failed to create payment order: " + safeMessage(ex),
                    HttpStatus.BAD_GATEWAY,
                    "CASHFREE_CREATE_ORDER_FAILED"
            );
        }
    }

    @Override
    public PaymentStatusResponseDto getPaymentStatus(String orderId) {
        log.info("Fetching payment status | orderId={}", orderId);

        try {
            ApiResponse<OrderEntity> orderResponse = cashfree.PGFetchOrder(orderId, null, null, null);
            OrderEntity order = orderResponse.getData();
            if (order == null) {
                throw new ResourceNotFoundException("Order not found: " + orderId);
            }

            List<PaymentStatusResponseDto.PaymentAttemptDto> payments = fetchPaymentAttempts(orderId);
            String aggregatedPaymentStatus = aggregatePaymentStatus(payments, order.getOrderStatus());

            return PaymentStatusResponseDto.builder()
                    .orderId(order.getOrderId())
                    .orderStatus(order.getOrderStatus())
                    .orderAmount(order.getOrderAmount())
                    .orderCurrency(order.getOrderCurrency())
                    .paymentStatus(aggregatedPaymentStatus)
                    .payments(payments)
                    .build();
        } catch (ResourceNotFoundException ex) {
            throw ex;
        } catch (ApiException ex) {
            if (ex.getCode() == 404) {
                throw new ResourceNotFoundException("Order not found: " + orderId);
            }
            log.error("Cashfree fetch-order failed | orderId={} code={} body={}",
                    orderId, ex.getCode(), ex.getResponseBody());
            throw new PaymentException(
                    "Failed to fetch payment status: " + safeMessage(ex),
                    HttpStatus.BAD_GATEWAY,
                    "CASHFREE_FETCH_ORDER_FAILED"
            );
        }
    }

    @Override
    public WebhookAckResponseDto handleWebhook(String rawBody, String signature, String timestamp) {
        if (!StringUtils.hasText(rawBody) || !StringUtils.hasText(signature) || !StringUtils.hasText(timestamp)) {
            throw new WebhookVerificationException("Missing webhook payload or signature headers");
        }

        try {
            // Verifies HMAC using server-side client secret. Throws if signature is invalid.
            cashfree.PGVerifyWebhookSignature(signature, rawBody, timestamp);
        } catch (WebhookVerificationException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("Webhook signature verification failed: {}", ex.getMessage());
            throw new WebhookVerificationException("Invalid Cashfree webhook signature", ex);
        }

        JsonNode payload = jsonNodeUtil.parse(rawBody);
        String eventType = jsonNodeUtil.textAt(payload, "type");
        String orderId = firstNonBlank(
                jsonNodeUtil.textAt(payload, "data", "order", "order_id"),
                jsonNodeUtil.textAt(payload, "data", "order_id")
        );
        String paymentStatus = jsonNodeUtil.textAt(payload, "data", "payment", "payment_status");

        log.info("Verified Cashfree webhook | type={} orderId={} paymentStatus={}",
                eventType, orderId, paymentStatus);

        // Future: notify Order / User services when payment succeeds or fails.
        orderEventPublisher.publishPaymentWebhook(orderId, eventType, paymentStatus, payload);

        return WebhookAckResponseDto.builder()
                .verified(true)
                .eventType(eventType)
                .orderId(orderId)
                .message("Webhook processed successfully")
                .build();
    }

    private CreateOrderRequest buildCreateOrderRequest(CreateOrderRequestDto request, String orderId) {
        CustomerDetailsDto customerDto = request.getCustomerDetails();

        CustomerDetails customerDetails = new CustomerDetails();
        customerDetails.setCustomerId(customerDto.getCustomerId());
        customerDetails.setCustomerPhone(customerDto.getCustomerPhone());
        if (StringUtils.hasText(customerDto.getCustomerEmail())) {
            customerDetails.setCustomerEmail(customerDto.getCustomerEmail());
        }
        if (StringUtils.hasText(customerDto.getCustomerName())) {
            customerDetails.setCustomerName(customerDto.getCustomerName());
        }

        OrderMeta orderMeta = new OrderMeta();
        orderMeta.setReturnUrl(appendOrderIdPlaceholder(cashfreeProperties.returnUrl()));
        orderMeta.setNotifyUrl(cashfreeProperties.notifyUrl());

        CreateOrderRequest cfRequest = new CreateOrderRequest();
        cfRequest.setOrderId(orderId);
        cfRequest.setOrderAmount(request.getOrderAmount());
        cfRequest.setOrderCurrency(request.getOrderCurrency());
        cfRequest.setCustomerDetails(customerDetails);
        cfRequest.setOrderMeta(orderMeta);

        if (StringUtils.hasText(request.getOrderNote())) {
            cfRequest.setOrderNote(request.getOrderNote());
        }

        Map<String, String> tags = new HashMap<>();
        if (request.getOrderTags() != null) {
            tags.putAll(request.getOrderTags());
        }
        if (StringUtils.hasText(request.getCartId())) {
            tags.put("cartId", request.getCartId());
        }
        if (StringUtils.hasText(request.getUserId())) {
            tags.put("userId", request.getUserId());
        }
        if (!tags.isEmpty()) {
            cfRequest.setOrderTags(tags);
        }

        return cfRequest;
    }

    private List<PaymentStatusResponseDto.PaymentAttemptDto> fetchPaymentAttempts(String orderId) {
        try {
            ApiResponse<List<PaymentEntity>> paymentsResponse =
                    cashfree.PGOrderFetchPayments(orderId, null, null, null);
            List<PaymentEntity> entities = paymentsResponse.getData();
            if (entities == null || entities.isEmpty()) {
                return Collections.emptyList();
            }
            return entities.stream().map(this::mapPayment).toList();
        } catch (ApiException ex) {
            log.warn("Unable to fetch payment attempts for orderId={}: {}", orderId, ex.getMessage());
            return Collections.emptyList();
        }
    }

    private PaymentStatusResponseDto.PaymentAttemptDto mapPayment(PaymentEntity entity) {
        String status = entity.getPaymentStatus() != null
                ? entity.getPaymentStatus().getValue()
                : null;

        return PaymentStatusResponseDto.PaymentAttemptDto.builder()
                .cfPaymentId(entity.getCfPaymentId())
                .paymentStatus(status)
                .paymentAmount(entity.getPaymentAmount())
                .paymentCurrency(entity.getPaymentCurrency())
                .paymentMethod(entity.getPaymentGroup())
                .paymentTime(entity.getPaymentTime())
                .build();
    }

    private String aggregatePaymentStatus(List<PaymentStatusResponseDto.PaymentAttemptDto> payments,
                                          String orderStatus) {
        if (payments == null || payments.isEmpty()) {
            return orderStatus;
        }
        boolean success = payments.stream()
                .anyMatch(p -> "SUCCESS".equalsIgnoreCase(p.getPaymentStatus()));
        if (success) {
            return "SUCCESS";
        }
        return payments.getFirst().getPaymentStatus();
    }

    private String appendOrderIdPlaceholder(String returnUrl) {
        if (!StringUtils.hasText(returnUrl)) {
            return null;
        }
        if (returnUrl.contains("{order_id}")) {
            return returnUrl;
        }
        String separator = returnUrl.contains("?") ? "&" : "?";
        return returnUrl + separator + "order_id={order_id}";
    }

    private String normalizeEnvironment(String environment) {
        if (environment != null && environment.equalsIgnoreCase("PRODUCTION")) {
            return "PRODUCTION";
        }
        return "SANDBOX";
    }

    private String safeMessage(ApiException ex) {
        if (StringUtils.hasText(ex.getResponseBody())) {
            return ex.getResponseBody();
        }
        return ex.getMessage() != null ? ex.getMessage() : "Unknown Cashfree error";
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }
}
