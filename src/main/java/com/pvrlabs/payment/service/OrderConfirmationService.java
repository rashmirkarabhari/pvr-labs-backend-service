package com.pvrlabs.payment.service;

import com.pvrlabs.payment.dto.response.PaymentStatusResponseDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderConfirmationService {

    private final CheckoutOrderStore checkoutOrderStore;
    private final OrderEmailService orderEmailService;

    /**
     * Sends admin + customer confirmation emails only after Cashfree reports a successful payment.
     * Idempotent across webhook retries and status polling.
     */
    public void notifyIfPaymentSuccessful(PaymentStatusResponseDto status) {
        if (status == null || !StringUtils.hasText(status.getOrderId())) {
            return;
        }
        if (!isPaid(status)) {
            return;
        }

        CheckoutOrderStore.Record record = checkoutOrderStore.getOrCreate(status.getOrderId());
        synchronized (record.getLock()) {
            record.setPaymentConfirmed(true);

            if (!record.isAdminEmailSent()) {
                try {
                    orderEmailService.sendAdminOrderEmail(status.getOrderId(), status, record);
                    record.setAdminEmailSent(true);
                } catch (Exception ex) {
                    log.error("Admin order email failed | orderId={}: {}", status.getOrderId(), ex.getMessage());
                }
            } else {
                log.info("Skipping duplicate admin order email | orderId={}", status.getOrderId());
            }

            if (!record.isCustomerEmailSent()) {
                try {
                    String customerEmail = orderEmailService.resolveCustomerEmail(record);
                    if (!orderEmailService.isValidEmail(customerEmail)) {
                        log.warn("Customer email missing/invalid; order remains paid | orderId={}", status.getOrderId());
                        record.setCustomerEmailSent(true);
                    } else {
                        orderEmailService.sendCustomerOrderEmail(status.getOrderId(), status, record);
                        record.setCustomerEmailSent(true);
                    }
                } catch (Exception ex) {
                    log.error("Customer order email failed | orderId={}: {}", status.getOrderId(), ex.getMessage());
                }
            } else {
                log.info("Skipping duplicate customer order email | orderId={}", status.getOrderId());
            }
        }
    }

    public static boolean isPaid(PaymentStatusResponseDto status) {
        String orderStatus = status.getOrderStatus() != null ? status.getOrderStatus().toUpperCase() : "";
        String paymentStatus = status.getPaymentStatus() != null ? status.getPaymentStatus().toUpperCase() : "";
        return "PAID".equals(orderStatus)
                || "CONFIRMED".equals(orderStatus)
                || "SUCCESS".equals(paymentStatus);
    }
}
