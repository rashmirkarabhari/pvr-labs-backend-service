package com.pvrlabs.payment.util;

import com.pvrlabs.payment.dto.request.CreateOrderRequestDto;
import com.pvrlabs.payment.dto.request.OrderItemSnapshotDto;
import com.pvrlabs.payment.dto.request.OrderSnapshotDto;
import com.pvrlabs.payment.exception.PaymentException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Server-side amount checks. Checkout totals from Angular are recalculated when a snapshot is present
 * and compared to Cashfree amounts on verification.
 */
@Component
public class OrderAmountValidator {

    public static final BigDecimal MIN_AMOUNT = new BigDecimal("1.00");
    private static final int SCALE = 2;

    public BigDecimal resolveChargeAmount(CreateOrderRequestDto request) {
        if (request == null || request.getOrderAmount() == null) {
            throw amountMismatch("Order amount is required");
        }
        BigDecimal requested = money(request.getOrderAmount());
        if (requested.compareTo(MIN_AMOUNT) < 0) {
            throw amountMismatch("Order amount is invalid");
        }

        OrderSnapshotDto snapshot = request.getOrderSnapshot();
        if (snapshot == null) {
            return requested;
        }

        BigDecimal calculated = calculateSnapshotTotal(snapshot);
        if (snapshot.getTotal() != null && money(snapshot.getTotal()).compareTo(calculated) != 0) {
            throw amountMismatch("Order amount is invalid");
        }
        if (calculated.compareTo(requested) != 0) {
            throw amountMismatch("Order amount is invalid");
        }
        return calculated;
    }

    public void assertPaidMatchesExpected(BigDecimal expectedAmount,
                                          BigDecimal cashfreeOrderAmount,
                                          BigDecimal paidAmount) {
        BigDecimal expected = expectedAmount != null ? money(expectedAmount) : null;
        if (expected == null) {
            if (cashfreeOrderAmount != null && paidAmount != null
                    && money(cashfreeOrderAmount).compareTo(money(paidAmount)) != 0) {
                throw amountMismatch("Payment amount does not match the order amount");
            }
            return;
        }
        if (cashfreeOrderAmount != null && money(cashfreeOrderAmount).compareTo(expected) != 0) {
            throw amountMismatch("Payment amount does not match the order amount");
        }
        if (paidAmount != null && money(paidAmount).compareTo(expected) != 0) {
            throw amountMismatch("Payment amount does not match the order amount");
        }
    }

    public BigDecimal calculateSnapshotTotal(OrderSnapshotDto snapshot) {
        BigDecimal itemsTotal = BigDecimal.ZERO;
        List<OrderItemSnapshotDto> items = snapshot.getItems();
        if (items != null) {
            for (OrderItemSnapshotDto item : items) {
                if (item == null) {
                    continue;
                }
                int quantity = item.getQuantity() != null ? item.getQuantity() : 0;
                if (quantity < 0) {
                    throw amountMismatch("Order amount is invalid");
                }
                BigDecimal price = money(item.getPrice());
                itemsTotal = itemsTotal.add(price.multiply(BigDecimal.valueOf(quantity)));
            }
        }

        if ((items == null || items.isEmpty()) && snapshot.getTotal() != null) {
            return money(snapshot.getTotal());
        }

        return money(
                itemsTotal
                        .add(nvl(snapshot.getDelivery()))
                        .add(nvl(snapshot.getTax()))
                        .add(nvl(snapshot.getGiftWrap()))
                        .subtract(nvl(snapshot.getDiscount()))
        );
    }

    public static BigDecimal money(BigDecimal value) {
        if (value == null) {
            return BigDecimal.ZERO.setScale(SCALE, RoundingMode.HALF_UP);
        }
        return value.setScale(SCALE, RoundingMode.HALF_UP);
    }

    private BigDecimal nvl(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private PaymentException amountMismatch(String message) {
        return new PaymentException(message, HttpStatus.BAD_REQUEST, "AMOUNT_MISMATCH");
    }
}
