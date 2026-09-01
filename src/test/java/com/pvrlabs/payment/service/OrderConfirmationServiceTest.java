package com.pvrlabs.payment.service;

import com.pvrlabs.payment.dto.response.PaymentStatusResponseDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderConfirmationServiceTest {

    @Mock
    private CheckoutOrderStore checkoutOrderStore;
    @Mock
    private OrderEmailService orderEmailService;

    @InjectMocks
    private OrderConfirmationService orderConfirmationService;

    @Test
    void doesNotEmailWhenPaymentIsNotSuccessful() {
        PaymentStatusResponseDto status = PaymentStatusResponseDto.builder()
                .orderId("PVR-1")
                .orderStatus("ACTIVE")
                .paymentStatus("PENDING")
                .orderAmount(new BigDecimal("200"))
                .build();

        orderConfirmationService.notifyIfPaymentSuccessful(status);

        verify(checkoutOrderStore, never()).getOrCreate(anyString());
        verify(orderEmailService, never()).sendAdminOrderEmail(anyString(), any(), any());
    }

    @Test
    void sendsEmailsOnceForDuplicatePaidEvents() {
        CheckoutOrderStore.Record record = new CheckoutOrderStore.Record("PVR-1");
        when(checkoutOrderStore.getOrCreate("PVR-1")).thenReturn(record);
        when(orderEmailService.resolveCustomerEmail(record)).thenReturn("buyer@example.com");
        when(orderEmailService.isValidEmail("buyer@example.com")).thenReturn(true);

        PaymentStatusResponseDto status = PaymentStatusResponseDto.builder()
                .orderId("PVR-1")
                .orderStatus("PAID")
                .paymentStatus("SUCCESS")
                .orderAmount(new BigDecimal("200"))
                .amountPaid(new BigDecimal("200"))
                .cfPaymentId("12345")
                .build();

        orderConfirmationService.notifyIfPaymentSuccessful(status);
        orderConfirmationService.notifyIfPaymentSuccessful(status);

        verify(orderEmailService, times(1)).sendAdminOrderEmail(anyString(), any(), any());
        verify(orderEmailService, times(1)).sendCustomerOrderEmail(anyString(), any(), any());
    }
}
