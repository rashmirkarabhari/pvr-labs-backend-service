package com.pvrlabs.payment.service;

import com.pvrlabs.payment.domain.PaymentStatuses;
import com.pvrlabs.payment.dto.request.CreateOrderRequestDto;
import com.pvrlabs.payment.dto.request.OrderSnapshotDto;
import lombok.Getter;
import lombok.Setter;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory checkout snapshot + verification flags for idempotent payment confirmation.
 * Survives Cashfree webhook retries on the same process; not shared across instances.
 */
@Component
public class CheckoutOrderStore {

    private final ConcurrentHashMap<String, Record> orders = new ConcurrentHashMap<>();

    public void savePending(String orderId, CreateOrderRequestDto request, BigDecimal expectedAmount) {
        orders.compute(orderId, (id, existing) -> {
            Record record = existing != null ? existing : new Record(id);
            record.setRequest(request);
            record.setExpectedAmount(expectedAmount);
            if (record.getPaymentStatus() == null) {
                record.setPaymentStatus(PaymentStatuses.PENDING);
            }
            if (record.getOrderStatus() == null) {
                record.setOrderStatus(PaymentStatuses.PENDING);
            }
            if (record.getCreatedAt() == null) {
                record.setCreatedAt(Instant.now());
            }
            return record;
        });
    }

    public Record getOrCreate(String orderId) {
        return orders.computeIfAbsent(orderId, Record::new);
    }

    public Record get(String orderId) {
        return orders.get(orderId);
    }

    public List<Record> listAll() {
        List<Record> snapshot = new ArrayList<>(orders.values());
        snapshot.sort(Comparator.comparing(Record::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())));
        return List.copyOf(snapshot);
    }

    /**
     * Updates fulfillment status only. Does not change payment flags or Cashfree fields.
     *
     * @return false when the order is not in this process store
     */
    public boolean updateOrderStatus(String orderId, String orderStatus) {
        Record record = orders.get(orderId);
        if (record == null) {
            return false;
        }
        synchronized (record.getLock()) {
            record.setOrderStatus(orderStatus);
        }
        return true;
    }

    /**
     * @return true if this is the first successful verification (caller should persist order side-effects)
     */
    public boolean markSuccessIfFirst(String orderId,
                                      BigDecimal amountPaid,
                                      String cfPaymentId,
                                      String cashfreeOrderStatus) {
        Record record = getOrCreate(orderId);
        synchronized (record.getLock()) {
            record.setCashfreeOrderStatus(cashfreeOrderStatus);
            record.setAmountPaid(amountPaid);
            record.setCfPaymentId(cfPaymentId);
            record.setPaymentStatus(PaymentStatuses.SUCCESS);
            record.setOrderStatus(PaymentStatuses.CONFIRMED);
            record.setLastVerifiedAt(Instant.now());
            if (record.isPaymentConfirmed() && record.isOrderItemsCommitted()) {
                return false;
            }
            record.setPaymentConfirmed(true);
            record.setOrderItemsCommitted(true);
            return true;
        }
    }

    public void markFailed(String orderId, String cashfreeOrderStatus) {
        Record record = getOrCreate(orderId);
        synchronized (record.getLock()) {
            if (record.isPaymentConfirmed()) {
                return;
            }
            record.setPaymentStatus(PaymentStatuses.FAILED);
            record.setCashfreeOrderStatus(cashfreeOrderStatus);
            record.setLastVerifiedAt(Instant.now());
        }
    }

    public void markPending(String orderId, String cashfreeOrderStatus) {
        Record record = getOrCreate(orderId);
        synchronized (record.getLock()) {
            if (record.isPaymentConfirmed()) {
                return;
            }
            record.setPaymentStatus(PaymentStatuses.PENDING);
            record.setOrderStatus(PaymentStatuses.PENDING);
            record.setCashfreeOrderStatus(cashfreeOrderStatus);
            record.setLastVerifiedAt(Instant.now());
        }
    }

    @Getter
    @Setter
    public static class Record {
        private final String orderId;
        private final Object lock = new Object();
        private CreateOrderRequestDto request;
        private Instant createdAt;
        private Instant lastVerifiedAt;
        private BigDecimal expectedAmount;
        private BigDecimal amountPaid;
        private String cfPaymentId;
        private String paymentStatus;
        private String orderStatus;
        private String cashfreeOrderStatus;
        private boolean paymentConfirmed;
        private boolean orderItemsCommitted;
        private boolean adminEmailSent;
        private boolean customerEmailSent;

        public Record(String orderId) {
            this.orderId = orderId;
            this.createdAt = Instant.now();
            this.paymentStatus = PaymentStatuses.PENDING;
            this.orderStatus = PaymentStatuses.PENDING;
        }

        public OrderSnapshotDto snapshot() {
            return request != null ? request.getOrderSnapshot() : null;
        }
    }
}
