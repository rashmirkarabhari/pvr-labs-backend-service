package com.pvrlabs.payment.service.impl;

import com.cashfree.pg.ApiException;
import com.cashfree.pg.model.OrderEntity;
import com.cashfree.pg.model.PaymentEntity;
import com.pvrlabs.payment.config.CashfreeProperties;
import com.pvrlabs.payment.domain.PaymentStatuses;
import com.pvrlabs.payment.dto.request.CreateOrderRequestDto;
import com.pvrlabs.payment.dto.request.CustomerDetailsDto;
import com.pvrlabs.payment.dto.request.OrderItemSnapshotDto;
import com.pvrlabs.payment.dto.request.OrderSnapshotDto;
import com.pvrlabs.payment.dto.response.CreateOrderResponseDto;
import com.pvrlabs.payment.dto.response.PaymentStatusResponseDto;
import com.pvrlabs.payment.exception.PaymentException;
import com.pvrlabs.payment.exception.ResourceNotFoundException;
import com.pvrlabs.payment.integration.CashfreeGateway;
import com.pvrlabs.payment.integration.OrderEventPublisher;
import com.pvrlabs.payment.service.CheckoutOrderStore;
import com.pvrlabs.payment.service.OrderConfirmationService;
import com.pvrlabs.payment.util.CashfreeErrorParser;
import com.pvrlabs.payment.util.JsonNodeUtil;
import com.pvrlabs.payment.util.OrderAmountValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentServiceImplTest {

    @Mock
    private CashfreeGateway cashfreeGateway;
    @Mock
    private OrderEventPublisher orderEventPublisher;
    @Mock
    private OrderConfirmationService orderConfirmationService;

    private CheckoutOrderStore checkoutOrderStore;
    private PaymentServiceImpl paymentService;

    @BeforeEach
    void setUp() {
        checkoutOrderStore = new CheckoutOrderStore();
        CashfreeProperties properties = new CashfreeProperties(
                "TEST1234abcd",
                "secret-not-logged",
                "SANDBOX",
                "2025-01-01",
                "http://localhost:4200/checkout?order_id={order_id}",
                ""
        );
        paymentService = new PaymentServiceImpl(
                cashfreeGateway,
                properties,
                new JsonNodeUtil(new ObjectMapper()),
                orderEventPublisher,
                new CashfreeErrorParser(new ObjectMapper()),
                checkoutOrderStore,
                orderConfirmationService,
                new OrderAmountValidator()
        );
    }

    @Test
    void createOrderSucceedsWithoutReturningSecrets() throws Exception {
        OrderEntity created = order("PVR-1", "ACTIVE", "sess_abc", "200.00");
        when(cashfreeGateway.createOrder(any())).thenReturn(created);

        CreateOrderResponseDto response = paymentService.createOrder(request("200.00", "PVR-1"));

        assertEquals("PVR-1", response.getOrderId());
        assertEquals("sess_abc", response.getPaymentSessionId());
        assertEquals(new BigDecimal("200.00"), response.getOrderAmount());
        assertEquals("SANDBOX", response.getEnvironment());
        assertNull(asJsonishSecret(response));
        verify(orderEventPublisher).publishOrderCreated("PVR-1", request("200.00", "PVR-1"));
    }

    @Test
    void verifyPaymentSuccessConfirmsOrder() throws Exception {
        checkoutOrderStore.savePending("PVR-1", request("200.00", "PVR-1"), new BigDecimal("200.00"));
        OrderEntity paid = order("PVR-1", "PAID", "sess_abc", "200.00");
        PaymentEntity successPayment = payment("pay_1", "SUCCESS", "200.00");
        when(cashfreeGateway.fetchOrder("PVR-1")).thenReturn(paid);
        when(cashfreeGateway.fetchPayments("PVR-1")).thenReturn(List.of(successPayment));

        PaymentStatusResponseDto status = paymentService.verifyPayment("PVR-1", null);

        assertEquals(PaymentStatuses.SUCCESS, status.getPaymentStatus());
        assertEquals(PaymentStatuses.CONFIRMED, status.getOrderStatus());
        assertEquals(new BigDecimal("200.00"), status.getAmountPaid());
        assertEquals("pay_1", status.getCfPaymentId());
        assertTrue(checkoutOrderStore.get("PVR-1").isOrderItemsCommitted());
        verify(orderConfirmationService).notifyIfPaymentSuccessful(any());
    }

    @Test
    void verifyPaymentFailedDoesNotConfirm() throws Exception {
        checkoutOrderStore.savePending("PVR-1", request("200.00", "PVR-1"), new BigDecimal("200.00"));
        OrderEntity active = order("PVR-1", "ACTIVE", "sess_abc", "200.00");
        PaymentEntity failedPayment = payment("pay_1", "FAILED", "200.00");
        when(cashfreeGateway.fetchOrder("PVR-1")).thenReturn(active);
        when(cashfreeGateway.fetchPayments("PVR-1")).thenReturn(List.of(failedPayment));

        PaymentStatusResponseDto status = paymentService.verifyPayment("PVR-1", null);

        assertEquals(PaymentStatuses.FAILED, status.getPaymentStatus());
        assertNull(status.getAmountPaid());
        verify(orderConfirmationService, times(0)).notifyIfPaymentSuccessful(any());
    }

    @Test
    void duplicateVerificationDoesNotReprocessOrder() throws Exception {
        checkoutOrderStore.savePending("PVR-1", request("200.00", "PVR-1"), new BigDecimal("200.00"));
        OrderEntity paid = order("PVR-1", "PAID", "sess_abc", "200.00");
        PaymentEntity successPayment = payment("pay_1", "SUCCESS", "200.00");
        when(cashfreeGateway.fetchOrder("PVR-1")).thenReturn(paid);
        when(cashfreeGateway.fetchPayments("PVR-1")).thenReturn(List.of(successPayment));

        paymentService.verifyPayment("PVR-1", null);
        PaymentStatusResponseDto second = paymentService.verifyPayment("PVR-1", null);

        assertEquals(PaymentStatuses.SUCCESS, second.getPaymentStatus());
        assertEquals(PaymentStatuses.CONFIRMED, second.getOrderStatus());
        verify(orderConfirmationService, times(1)).notifyIfPaymentSuccessful(any());
    }

    @Test
    void invalidOrderReturnsNotFound() throws Exception {
        when(cashfreeGateway.fetchOrder("missing")).thenThrow(new ApiException("not found", 404, null, "{}"));

        assertThrows(ResourceNotFoundException.class, () -> paymentService.getPaymentStatus("missing"));
    }

    @Test
    void invalidPaymentIdReturnsNotFound() throws Exception {
        OrderEntity paid = order("PVR-1", "PAID", "sess_abc", "200.00");
        PaymentEntity successPayment = payment("pay_1", "SUCCESS", "200.00");
        when(cashfreeGateway.fetchOrder("PVR-1")).thenReturn(paid);
        when(cashfreeGateway.fetchPayments("PVR-1")).thenReturn(List.of(successPayment));

        assertThrows(ResourceNotFoundException.class, () -> paymentService.verifyPayment("PVR-1", "pay_other"));
    }

    @Test
    void amountMismatchIsRejected() throws Exception {
        checkoutOrderStore.savePending("PVR-1", request("200.00", "PVR-1"), new BigDecimal("200.00"));
        OrderEntity paidWrongAmount = order("PVR-1", "PAID", "sess_abc", "50.00");
        PaymentEntity wrongPayment = payment("pay_1", "SUCCESS", "50.00");
        when(cashfreeGateway.fetchOrder("PVR-1")).thenReturn(paidWrongAmount);
        when(cashfreeGateway.fetchPayments("PVR-1")).thenReturn(List.of(wrongPayment));

        PaymentException ex = assertThrows(PaymentException.class, () -> paymentService.verifyPayment("PVR-1", null));
        assertEquals("AMOUNT_MISMATCH", ex.getCode());
        verify(orderConfirmationService, times(0)).notifyIfPaymentSuccessful(any());
    }

    @Test
    void cashfreeApiFailureIsMapped() throws Exception {
        when(cashfreeGateway.fetchOrder("PVR-1")).thenThrow(new ApiException("down", 502, null, "{\"message\":\"unavailable\"}"));

        PaymentException ex = assertThrows(PaymentException.class, () -> paymentService.getPaymentStatus("PVR-1"));
        assertEquals(502, ex.getStatus().value());
    }

    @Test
    void cashfreeNetworkFailureIsMapped() throws Exception {
        when(cashfreeGateway.fetchOrder("PVR-1")).thenThrow(new RuntimeException("connection reset"));

        PaymentException ex = assertThrows(PaymentException.class, () -> paymentService.getPaymentStatus("PVR-1"));
        assertEquals("CASHFREE_UNAVAILABLE", ex.getCode());
        assertEquals(502, ex.getStatus().value());
    }

    @Test
    void createOrderRejectsSnapshotAmountMismatch() {
        CreateOrderRequestDto request = request("100.00", "PVR-1");
        request.setOrderSnapshot(OrderSnapshotDto.builder()
                .items(List.of(OrderItemSnapshotDto.builder().price(new BigDecimal("80.00")).quantity(1).build()))
                .delivery(BigDecimal.ZERO)
                .tax(BigDecimal.ZERO)
                .discount(BigDecimal.ZERO)
                .giftWrap(BigDecimal.ZERO)
                .total(new BigDecimal("80.00"))
                .build());

        PaymentException ex = assertThrows(PaymentException.class, () -> paymentService.createOrder(request));
        assertEquals("AMOUNT_MISMATCH", ex.getCode());
    }

    private CreateOrderRequestDto request(String amount, String orderId) {
        return CreateOrderRequestDto.builder()
                .orderId(orderId)
                .orderAmount(new BigDecimal(amount))
                .orderCurrency("INR")
                .customerDetails(CustomerDetailsDto.builder()
                        .customerId("USR100")
                        .customerPhone("9380930486")
                        .customerEmail("buyer@example.com")
                        .customerName("Asha")
                        .build())
                .build();
    }

    private OrderEntity order(String orderId, String status, String sessionId, String amount) {
        OrderEntity entity = mock(OrderEntity.class);
        lenient().when(entity.getOrderId()).thenReturn(orderId);
        lenient().when(entity.getOrderStatus()).thenReturn(status);
        lenient().when(entity.getPaymentSessionId()).thenReturn(sessionId);
        lenient().when(entity.getOrderAmount()).thenReturn(new BigDecimal(amount));
        lenient().when(entity.getOrderCurrency()).thenReturn("INR");
        return entity;
    }

    private PaymentEntity payment(String paymentId, String status, String amount) {
        PaymentEntity entity = mock(PaymentEntity.class);
        lenient().when(entity.getCfPaymentId()).thenReturn(paymentId);
        lenient().when(entity.getPaymentStatus()).thenReturn(PaymentEntity.PaymentStatusEnum.fromValue(status));
        lenient().when(entity.getPaymentAmount()).thenReturn(new BigDecimal(amount));
        lenient().when(entity.getPaymentCurrency()).thenReturn("INR");
        return entity;
    }

    private Object asJsonishSecret(CreateOrderResponseDto response) {
        String text = response.toString().toLowerCase();
        assertTrue(!text.contains("secret") && !text.contains("cfsk"));
        return null;
    }
}
