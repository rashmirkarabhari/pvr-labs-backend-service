package com.pvrlabs.payment.service.impl;

import com.cashfree.pg.ApiException;
import com.cashfree.pg.Cashfree;
import com.cashfree.pg.model.CreateOrderRequest;
import com.cashfree.pg.model.CustomerDetails;
import com.cashfree.pg.model.OrderEntity;
import com.cashfree.pg.model.OrderMeta;
import com.cashfree.pg.model.PaymentEntity;
import com.fasterxml.jackson.databind.JsonNode;
import com.pvrlabs.payment.config.CashfreeConfig;
import com.pvrlabs.payment.config.CashfreeProperties;
import com.pvrlabs.payment.domain.PaymentStatuses;
import com.pvrlabs.payment.dto.request.CreateOrderRequestDto;
import com.pvrlabs.payment.dto.request.CustomerDetailsDto;
import com.pvrlabs.payment.dto.response.CreateOrderResponseDto;
import com.pvrlabs.payment.dto.response.PaymentStatusResponseDto;
import com.pvrlabs.payment.dto.response.WebhookAckResponseDto;
import com.pvrlabs.payment.exception.PaymentException;
import com.pvrlabs.payment.exception.ResourceNotFoundException;
import com.pvrlabs.payment.exception.WebhookVerificationException;
import com.pvrlabs.payment.integration.CashfreeGateway;
import com.pvrlabs.payment.integration.OrderEventPublisher;
import com.pvrlabs.payment.service.CheckoutOrderStore;
import com.pvrlabs.payment.service.OrderConfirmationService;
import com.pvrlabs.payment.service.PaymentService;
import com.pvrlabs.payment.util.CashfreeErrorParser;
import com.pvrlabs.payment.util.CustomerPhoneNormalizer;
import com.pvrlabs.payment.util.JsonNodeUtil;
import com.pvrlabs.payment.util.OrderAmountValidator;
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

    private final CashfreeGateway cashfreeGateway;
    private final CashfreeProperties cashfreeProperties;
    private final JsonNodeUtil jsonNodeUtil;
    private final OrderEventPublisher orderEventPublisher;
    private final CashfreeErrorParser cashfreeErrorParser;
    private final CheckoutOrderStore checkoutOrderStore;
    private final OrderConfirmationService orderConfirmationService;
    private final OrderAmountValidator orderAmountValidator;

    private static final String INR = "INR";

    @Override
    public CreateOrderResponseDto createOrder(CreateOrderRequestDto request) {
        BigDecimal chargeAmount = orderAmountValidator.resolveChargeAmount(request);

        String orderId = StringUtils.hasText(request.getOrderId())
                ? request.getOrderId().trim()
                : OrderIdGenerator.generate();

        CheckoutOrderStore.Record existing = checkoutOrderStore.get(orderId);
        if (existing != null && existing.isPaymentConfirmed()) {
            throw new PaymentException(
                    "This order has already been paid",
                    HttpStatus.CONFLICT,
                    "ORDER_ALREADY_PAID"
            );
        }

        String environment = normalizeEnvironment(cashfreeProperties.environment());
        String endpoint = CashfreeConfig.baseUrlFor(
                "PRODUCTION".equals(environment) ? Cashfree.PRODUCTION : Cashfree.SANDBOX
        ) + CashfreeConfig.CREATE_ORDER_PATH;

        log.info(
                "Payment order creation | environment={} endpoint={} orderId={} amount={} {} clientId={}",
                environment,
                endpoint,
                orderId,
                chargeAmount,
                INR,
                CashfreeConfig.maskClientId(cashfreeProperties.clientId())
        );

        try {
            CreateOrderRequest cfRequest = buildCreateOrderRequest(request, orderId, chargeAmount);
            OrderEntity order = cashfreeGateway.createOrder(cfRequest);

            if (order == null) {
                throw new PaymentException(
                        "Payment gateway returned an empty order response",
                        HttpStatus.BAD_GATEWAY,
                        "CASHFREE_EMPTY_RESPONSE"
                );
            }

            if (!StringUtils.hasText(order.getPaymentSessionId())) {
                log.error(
                        "Cashfree create-order succeeded but payment_session_id missing | orderId={} orderStatus={}",
                        order.getOrderId(),
                        order.getOrderStatus()
                );
                throw new PaymentException(
                        "Payment session is unavailable. Please retry checkout.",
                        HttpStatus.BAD_GATEWAY,
                        "CASHFREE_MISSING_PAYMENT_SESSION"
                );
            }

            log.info(
                    "Payment order created | orderId={} amount={} currency={} orderStatus={} paymentSessionIdPresent=true",
                    order.getOrderId(),
                    order.getOrderAmount(),
                    order.getOrderCurrency(),
                    order.getOrderStatus()
            );

            orderEventPublisher.publishOrderCreated(order.getOrderId(), request);
            checkoutOrderStore.savePending(order.getOrderId(), request, chargeAmount);

            return toCreateOrderResponse(order, environment);
        } catch (PaymentException ex) {
            throw ex;
        } catch (ApiException ex) {
            if (isDuplicateCashfreeOrder(ex)) {
                log.info("Cashfree reported existing order; fetching | orderId={}", orderId);
                return recoverExistingCashfreeOrder(orderId, request, chargeAmount, environment, endpoint);
            }
            throw toPaymentException("create-order", orderId, endpoint, chargeAmount, ex);
        } catch (Exception ex) {
            throw wrapUnexpectedGatewayFailure("create-order", orderId, ex);
        }
    }

    @Override
    public PaymentStatusResponseDto getPaymentStatus(String orderId) {
        return verifyPayment(orderId, null);
    }

    @Override
    public PaymentStatusResponseDto verifyPayment(String orderId, String paymentId) {
        if (!StringUtils.hasText(orderId)) {
            throw new PaymentException("Order ID is invalid", HttpStatus.BAD_REQUEST, "INVALID_ORDER_ID");
        }
        String trimmedOrderId = orderId.trim();
        String trimmedPaymentId = StringUtils.hasText(paymentId) ? paymentId.trim() : null;

        String environment = normalizeEnvironment(cashfreeProperties.environment());
        String endpoint = CashfreeConfig.baseUrlFor(
                "PRODUCTION".equals(environment) ? Cashfree.PRODUCTION : Cashfree.SANDBOX
        ) + "/orders/" + trimmedOrderId;

        log.info("Payment verification attempt | environment={} orderId={} paymentIdPresent={}",
                environment, trimmedOrderId, trimmedPaymentId != null);

        try {
            OrderEntity order = cashfreeGateway.fetchOrder(trimmedOrderId);
            if (order == null) {
                log.info("Payment verification failed | orderId={} reason=order_not_found", trimmedOrderId);
                throw new ResourceNotFoundException("Order not found");
            }

            List<PaymentStatusResponseDto.PaymentAttemptDto> payments = fetchPaymentAttempts(trimmedOrderId);
            if (trimmedPaymentId != null) {
                payments = filterToPaymentId(payments, trimmedPaymentId);
            }

            String cashfreeOrderStatus = order.getOrderStatus() != null ? String.valueOf(order.getOrderStatus()) : null;
            String aggregatedPaymentStatus = aggregatePaymentStatus(payments, cashfreeOrderStatus);
            PaymentStatusResponseDto.PaymentAttemptDto successfulPayment = findSuccessfulPayment(payments);

            if (PaymentStatuses.SUCCESS.equals(aggregatedPaymentStatus)) {
                BigDecimal paidAmount = resolveSuccessfulPaidAmount(successfulPayment, order.getOrderAmount());
                BigDecimal expected = expectedAmountFor(trimmedOrderId);
                orderAmountValidator.assertPaidMatchesExpected(expected, order.getOrderAmount(), paidAmount);

                boolean firstConfirmation = checkoutOrderStore.markSuccessIfFirst(
                        trimmedOrderId,
                        paidAmount,
                        successfulPayment != null ? successfulPayment.getCfPaymentId() : null,
                        cashfreeOrderStatus
                );

                PaymentStatusResponseDto status = buildStatusResponse(
                        order,
                        PaymentStatuses.CONFIRMED,
                        PaymentStatuses.SUCCESS,
                        paidAmount,
                        successfulPayment,
                        payments
                );

                if (firstConfirmation) {
                    log.info("Payment verification success | orderId={} paymentStatus={} orderStatus={} firstConfirmation=true",
                            trimmedOrderId, PaymentStatuses.SUCCESS, PaymentStatuses.CONFIRMED);
                    orderConfirmationService.notifyIfPaymentSuccessful(status);
                } else {
                    log.info("Payment verification duplicate | orderId={} paymentStatus={} orderStatus={}",
                            trimmedOrderId, PaymentStatuses.SUCCESS, PaymentStatuses.CONFIRMED);
                }
                return status;
            }

            if (PaymentStatuses.FAILED.equals(aggregatedPaymentStatus)) {
                checkoutOrderStore.markFailed(trimmedOrderId, cashfreeOrderStatus);
                log.info("Payment verification failed | orderId={} paymentStatus={}",
                        trimmedOrderId, PaymentStatuses.FAILED);
                return buildStatusResponse(
                        order,
                        cashfreeOrderStatus,
                        PaymentStatuses.FAILED,
                        null,
                        null,
                        payments
                );
            }

            checkoutOrderStore.markPending(trimmedOrderId, cashfreeOrderStatus);
            log.info("Payment verification pending | orderId={} paymentStatus={} cashfreeOrderStatus={}",
                    trimmedOrderId, PaymentStatuses.PENDING, cashfreeOrderStatus);
            return buildStatusResponse(
                    order,
                    cashfreeOrderStatus,
                    PaymentStatuses.PENDING,
                    null,
                    null,
                    payments
            );
        } catch (PaymentException ex) {
            throw ex;
        } catch (ApiException ex) {
            if (ex.getCode() == 404) {
                log.info("Payment verification failed | orderId={} reason=order_not_found", trimmedOrderId);
                throw new ResourceNotFoundException("Order not found");
            }
            throw toPaymentException("verify-payment", trimmedOrderId, endpoint, null, ex);
        } catch (Exception ex) {
            throw wrapUnexpectedGatewayFailure("verify-payment", trimmedOrderId, ex);
        }
    }

    @Override
    public WebhookAckResponseDto handleWebhook(String rawBody, String signature, String timestamp) {
        if (!StringUtils.hasText(rawBody) || !StringUtils.hasText(signature) || !StringUtils.hasText(timestamp)) {
            throw new WebhookVerificationException("Missing webhook payload or signature headers");
        }

        try {
            cashfreeGateway.verifyWebhookSignature(signature, rawBody, timestamp);
        } catch (WebhookVerificationException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("Webhook signature verification failed");
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

        orderEventPublisher.publishPaymentWebhook(orderId, eventType, paymentStatus, payload);

        if (StringUtils.hasText(orderId)) {
            try {
                PaymentStatusResponseDto verified = verifyPayment(orderId, null);
                log.info(
                        "Webhook payment re-verified with Cashfree | orderId={} orderStatus={} paymentStatus={}",
                        orderId,
                        verified.getOrderStatus(),
                        verified.getPaymentStatus()
                );
            } catch (Exception ex) {
                log.error("Webhook accepted but Cashfree re-verification failed | orderId={}", orderId);
            }
        }

        return WebhookAckResponseDto.builder()
                .verified(true)
                .eventType(eventType)
                .orderId(orderId)
                .message("Webhook processed successfully")
                .build();
    }

    /**
     * Builds Cashfree Create Order body.
     * Does NOT set payment_methods — leaving it blank keeps Card, UPI, and other enabled modes available in Hosted Checkout.
     * Charge amount is the server-validated amount, not a raw untrusted extra field.
     */
    private CreateOrderRequest buildCreateOrderRequest(CreateOrderRequestDto request,
                                                       String orderId,
                                                       BigDecimal chargeAmount) {
        CustomerDetailsDto customerDto = request.getCustomerDetails();

        CustomerDetails customerDetails = new CustomerDetails();
        customerDetails.setCustomerId(sanitizeCustomerId(customerDto.getCustomerId()));
        customerDetails.setCustomerPhone(CustomerPhoneNormalizer.toIndianMobile(customerDto.getCustomerPhone()));
        if (StringUtils.hasText(customerDto.getCustomerEmail())) {
            customerDetails.setCustomerEmail(customerDto.getCustomerEmail().trim());
        }
        if (StringUtils.hasText(customerDto.getCustomerName())) {
            customerDetails.setCustomerName(customerDto.getCustomerName().trim());
        }

        OrderMeta orderMeta = new OrderMeta();
        orderMeta.setReturnUrl(appendOrderIdPlaceholder(cashfreeProperties.returnUrl()));
        String notifyUrl = cashfreeProperties.notifyUrl();
        if (isHttpsUrl(notifyUrl)) {
            orderMeta.setNotifyUrl(notifyUrl.trim());
        } else if (StringUtils.hasText(notifyUrl)) {
            log.warn(
                    "Skipping Cashfree notify_url because it is not HTTPS. "
                            + "Use an HTTPS tunnel for local webhook testing."
            );
        }

        CreateOrderRequest cfRequest = new CreateOrderRequest();
        cfRequest.setOrderId(orderId);
        cfRequest.setOrderAmount(chargeAmount);
        if (StringUtils.hasText(request.getOrderCurrency())
                && !INR.equalsIgnoreCase(request.getOrderCurrency())) {
            log.warn("Overriding requested currency {} to INR | orderId={}", request.getOrderCurrency(), orderId);
        }
        cfRequest.setOrderCurrency(INR);
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

    private CreateOrderResponseDto recoverExistingCashfreeOrder(String orderId,
                                                                CreateOrderRequestDto request,
                                                                BigDecimal chargeAmount,
                                                                String environment,
                                                                String endpoint) {
        try {
            OrderEntity order = cashfreeGateway.fetchOrder(orderId);
            if (order == null || !StringUtils.hasText(order.getPaymentSessionId())) {
                throw new PaymentException(
                        "An order with this ID already exists",
                        HttpStatus.CONFLICT,
                        "ORDER_ALREADY_EXISTS"
                );
            }
            checkoutOrderStore.savePending(order.getOrderId(), request, chargeAmount);
            log.info("Reused existing Cashfree order | orderId={} orderStatus={}",
                    order.getOrderId(), order.getOrderStatus());
            return toCreateOrderResponse(order, environment);
        } catch (PaymentException ex) {
            throw ex;
        } catch (ApiException ex) {
            throw toPaymentException("create-order", orderId, endpoint, chargeAmount, ex);
        }
    }

    private CreateOrderResponseDto toCreateOrderResponse(OrderEntity order, String environment) {
        return CreateOrderResponseDto.builder()
                .orderId(order.getOrderId())
                .paymentSessionId(order.getPaymentSessionId())
                .orderAmount(order.getOrderAmount())
                .orderCurrency(order.getOrderCurrency())
                .orderStatus(order.getOrderStatus() != null ? String.valueOf(order.getOrderStatus()) : null)
                .environment(environment)
                .build();
    }

    private PaymentStatusResponseDto buildStatusResponse(OrderEntity order,
                                                         String orderStatus,
                                                         String paymentStatus,
                                                         BigDecimal amountPaid,
                                                         PaymentStatusResponseDto.PaymentAttemptDto successfulPayment,
                                                         List<PaymentStatusResponseDto.PaymentAttemptDto> payments) {
        return PaymentStatusResponseDto.builder()
                .orderId(order.getOrderId())
                .orderStatus(orderStatus)
                .orderAmount(order.getOrderAmount())
                .orderCurrency(StringUtils.hasText(order.getOrderCurrency()) ? order.getOrderCurrency() : INR)
                .paymentStatus(paymentStatus)
                .amountPaid(amountPaid)
                .cfPaymentId(successfulPayment != null ? successfulPayment.getCfPaymentId() : null)
                .payments(payments)
                .build();
    }

    private PaymentException toPaymentException(String operation, String orderId, String endpoint,
                                                Object amount, ApiException ex) {
        CashfreeErrorParser.ParsedError parsed = cashfreeErrorParser.parse(ex.getCode(), ex.getResponseBody());

        log.error(
                "Cashfree {} failed | environment={} endpoint={} orderId={} amount={} httpStatus={} cashfreeCode={} cashfreeType={}",
                operation,
                normalizeEnvironment(cashfreeProperties.environment()),
                endpoint,
                orderId,
                amount,
                ex.getCode(),
                parsed.code(),
                parsed.cashfreeType()
        );

        return new PaymentException(
                enrichMerchantFacingMessage(parsed.message()),
                parsed.status(),
                parsed.code(),
                parsed.cashfreeType(),
                null
        );
    }

    private PaymentException wrapUnexpectedGatewayFailure(String operation, String orderId, Exception ex) {
        log.error("Cashfree {} failed | orderId={} reason=unavailable type={}",
                operation, orderId, ex.getClass().getSimpleName());
        return new PaymentException(
                "Unable to reach the payment gateway. Please try again.",
                HttpStatus.BAD_GATEWAY,
                "CASHFREE_UNAVAILABLE",
                null,
                null
        );
    }

    private String enrichMerchantFacingMessage(String cashfreeMessage) {
        if (!StringUtils.hasText(cashfreeMessage)) {
            return "Payment request failed";
        }
        String lower = cashfreeMessage.toLowerCase();
        if (lower.contains("transactions are not enabled")
                || (lower.contains("payment gateway") && lower.contains("not enabled"))) {
            return cashfreeMessage
                    + " — In Cashfree Merchant Dashboard: ensure you are in TEST ENVIRONMENT, "
                    + "open Developers -> API Keys and copy TEST App ID / Secret, then open "
                    + "Payment Gateway -> Settings -> Payment Methods and enable Card and UPI. "
                    + "If Payment Gateway is not activated for the account, complete PG onboarding / contact Cashfree support.";
        }
        return cashfreeMessage;
    }

    private List<PaymentStatusResponseDto.PaymentAttemptDto> fetchPaymentAttempts(String orderId) {
        try {
            List<PaymentEntity> entities = cashfreeGateway.fetchPayments(orderId);
            if (entities == null || entities.isEmpty()) {
                return Collections.emptyList();
            }
            return entities.stream().map(this::mapPayment).toList();
        } catch (ApiException ex) {
            if (ex.getCode() >= 500 || ex.getCode() <= 0) {
                throw toPaymentException("fetch-payments", orderId, "payments", null, ex);
            }
            log.warn("Unable to fetch payment attempts for orderId={} httpStatus={}", orderId, ex.getCode());
            return Collections.emptyList();
        }
    }

    private PaymentStatusResponseDto.PaymentAttemptDto mapPayment(PaymentEntity entity) {
        String status = entity.getPaymentStatus() != null
                ? entity.getPaymentStatus().getValue()
                : null;

        return PaymentStatusResponseDto.PaymentAttemptDto.builder()
                .cfPaymentId(entity.getCfPaymentId() != null ? String.valueOf(entity.getCfPaymentId()) : null)
                .paymentStatus(status)
                .paymentAmount(entity.getPaymentAmount())
                .paymentCurrency(entity.getPaymentCurrency())
                .paymentMethod(entity.getPaymentGroup())
                .paymentTime(entity.getPaymentTime())
                .build();
    }

    private List<PaymentStatusResponseDto.PaymentAttemptDto> filterToPaymentId(
            List<PaymentStatusResponseDto.PaymentAttemptDto> payments,
            String paymentId) {
        List<PaymentStatusResponseDto.PaymentAttemptDto> matched = payments.stream()
                .filter(p -> paymentId.equals(p.getCfPaymentId()))
                .toList();
        if (matched.isEmpty()) {
            throw new ResourceNotFoundException("Payment not found");
        }
        return matched;
    }

    private PaymentStatusResponseDto.PaymentAttemptDto findSuccessfulPayment(
            List<PaymentStatusResponseDto.PaymentAttemptDto> payments) {
        if (payments == null) {
            return null;
        }
        return payments.stream()
                .filter(p -> p.getPaymentStatus() != null && PaymentStatuses.SUCCESS.equalsIgnoreCase(p.getPaymentStatus()))
                .findFirst()
                .orElse(null);
    }

    private BigDecimal resolveSuccessfulPaidAmount(PaymentStatusResponseDto.PaymentAttemptDto successfulPayment,
                                                   BigDecimal orderAmount) {
        if (successfulPayment != null && successfulPayment.getPaymentAmount() != null) {
            return successfulPayment.getPaymentAmount();
        }
        return orderAmount;
    }

    private BigDecimal expectedAmountFor(String orderId) {
        CheckoutOrderStore.Record record = checkoutOrderStore.get(orderId);
        return record != null ? record.getExpectedAmount() : null;
    }

    private String aggregatePaymentStatus(List<PaymentStatusResponseDto.PaymentAttemptDto> payments,
                                          String orderStatus) {
        String cashfreeOrder = orderStatus != null ? orderStatus.toUpperCase() : "";
        if (PaymentStatuses.CASHFREE_PAID.equals(cashfreeOrder)) {
            return PaymentStatuses.SUCCESS;
        }

        if (payments != null) {
            boolean success = payments.stream()
                    .anyMatch(p -> PaymentStatuses.SUCCESS.equalsIgnoreCase(p.getPaymentStatus()));
            if (success) {
                return PaymentStatuses.SUCCESS;
            }
            boolean pending = payments.stream()
                    .anyMatch(p -> isPendingAttempt(p.getPaymentStatus()));
            if (pending) {
                return PaymentStatuses.PENDING;
            }
            boolean failed = payments.stream()
                    .anyMatch(p -> isFailedAttempt(p.getPaymentStatus()));
            if (failed) {
                return PaymentStatuses.FAILED;
            }
        }

        if (PaymentStatuses.CASHFREE_EXPIRED.equals(cashfreeOrder)
                || PaymentStatuses.FAILED.equals(cashfreeOrder)) {
            return PaymentStatuses.FAILED;
        }
        return PaymentStatuses.PENDING;
    }

    private boolean isPendingAttempt(String status) {
        if (!StringUtils.hasText(status)) {
            return false;
        }
        String value = status.toUpperCase();
        return PaymentStatuses.PENDING.equals(value)
                || "NOT_ATTEMPTED".equals(value)
                || "INCOMPLETE".equals(value)
                || "FLAGGED".equals(value);
    }

    private boolean isFailedAttempt(String status) {
        if (!StringUtils.hasText(status)) {
            return false;
        }
        String value = status.toUpperCase();
        return PaymentStatuses.FAILED.equals(value)
                || "USER_DROPPED".equals(value)
                || "CANCELLED".equals(value)
                || "VOID".equals(value)
                || "EXPIRED".equals(value);
    }

    private boolean isDuplicateCashfreeOrder(ApiException ex) {
        if (ex.getCode() == 409) {
            return true;
        }
        String body = ex.getResponseBody();
        if (!StringUtils.hasText(body)) {
            return false;
        }
        String lower = body.toLowerCase();
        return lower.contains("already exists") || lower.contains("order_already_exists");
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

    private boolean isHttpsUrl(String url) {
        return StringUtils.hasText(url) && url.trim().regionMatches(true, 0, "https://", 0, 8);
    }

    private String sanitizeCustomerId(String customerId) {
        String sanitized = customerId == null ? "" : customerId.replaceAll("[^A-Za-z0-9]", "");
        if (!StringUtils.hasText(sanitized)) {
            throw new PaymentException(
                    "customerId must contain at least one alphanumeric character",
                    HttpStatus.BAD_REQUEST,
                    "INVALID_CUSTOMER_ID"
            );
        }
        if (sanitized.length() > 50) {
            sanitized = sanitized.substring(0, 50);
        }
        return sanitized;
    }

    private String normalizeEnvironment(String environment) {
        if (environment != null && environment.equalsIgnoreCase("PRODUCTION")) {
            return "PRODUCTION";
        }
        return "SANDBOX";
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
