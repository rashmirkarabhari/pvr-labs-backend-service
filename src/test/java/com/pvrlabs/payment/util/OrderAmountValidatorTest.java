package com.pvrlabs.payment.util;

import com.pvrlabs.payment.dto.request.CreateOrderRequestDto;
import com.pvrlabs.payment.dto.request.OrderItemSnapshotDto;
import com.pvrlabs.payment.dto.request.OrderSnapshotDto;
import com.pvrlabs.payment.exception.PaymentException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OrderAmountValidatorTest {

    private final OrderAmountValidator validator = new OrderAmountValidator();

    @Test
    void usesRequestedAmountWhenSnapshotMissing() {
        CreateOrderRequestDto request = CreateOrderRequestDto.builder()
                .orderAmount(new BigDecimal("1499.5"))
                .build();

        assertEquals(new BigDecimal("1499.50"), validator.resolveChargeAmount(request));
    }

    @Test
    void recalculatesSnapshotAndRejectsMismatch() {
        CreateOrderRequestDto request = CreateOrderRequestDto.builder()
                .orderAmount(new BigDecimal("100.00"))
                .orderSnapshot(OrderSnapshotDto.builder()
                        .items(List.of(OrderItemSnapshotDto.builder()
                                .price(new BigDecimal("50.00"))
                                .quantity(2)
                                .build()))
                        .delivery(new BigDecimal("40.00"))
                        .tax(BigDecimal.ZERO)
                        .discount(BigDecimal.ZERO)
                        .giftWrap(BigDecimal.ZERO)
                        .total(new BigDecimal("140.00"))
                        .build())
                .build();

        PaymentException ex = assertThrows(PaymentException.class, () -> validator.resolveChargeAmount(request));
        assertEquals("AMOUNT_MISMATCH", ex.getCode());
    }

    @Test
    void acceptsMatchingSnapshotTotal() {
        CreateOrderRequestDto request = CreateOrderRequestDto.builder()
                .orderAmount(new BigDecimal("140.00"))
                .orderSnapshot(OrderSnapshotDto.builder()
                        .items(List.of(OrderItemSnapshotDto.builder()
                                .price(new BigDecimal("50.00"))
                                .quantity(2)
                                .build()))
                        .delivery(new BigDecimal("40.00"))
                        .tax(BigDecimal.ZERO)
                        .discount(BigDecimal.ZERO)
                        .giftWrap(BigDecimal.ZERO)
                        .total(new BigDecimal("140.00"))
                        .build())
                .build();

        assertEquals(new BigDecimal("140.00"), validator.resolveChargeAmount(request));
    }

    @Test
    void rejectsPaidAmountDifferentFromExpected() {
        PaymentException ex = assertThrows(PaymentException.class, () ->
                validator.assertPaidMatchesExpected(
                        new BigDecimal("140.00"),
                        new BigDecimal("140.00"),
                        new BigDecimal("99.00")));
        assertEquals("AMOUNT_MISMATCH", ex.getCode());
    }
}
