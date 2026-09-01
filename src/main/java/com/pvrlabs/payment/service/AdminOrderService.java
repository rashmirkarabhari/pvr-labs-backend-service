package com.pvrlabs.payment.service;

import com.pvrlabs.payment.domain.OrderStatuses;
import com.pvrlabs.payment.domain.PaymentStatuses;
import com.pvrlabs.payment.dto.request.CreateOrderRequestDto;
import com.pvrlabs.payment.dto.request.UpdateOrderStatusRequestDto;
import com.pvrlabs.payment.dto.response.AdminDashboardResponseDto;
import com.pvrlabs.payment.dto.response.AdminOrderResponseDto;
import com.pvrlabs.payment.exception.PaymentException;
import com.pvrlabs.payment.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminOrderService {

    private final CheckoutOrderStore checkoutOrderStore;
    private final ProductCatalog productCatalog;

    public List<AdminOrderResponseDto> listOrders() {
        return checkoutOrderStore.listAll().stream()
                .map(this::toDto)
                .toList();
    }

    public AdminOrderResponseDto getOrder(String orderId) {
        CheckoutOrderStore.Record record = requireRecord(orderId);
        return toDto(record);
    }

    public AdminOrderResponseDto updateStatus(String orderId, UpdateOrderStatusRequestDto request) {
        requireRecord(orderId);
        String status = request.getStatus();
        if (!OrderStatuses.isSupported(status)) {
            throw new PaymentException(
                    "Unsupported order status: " + status
                            + ". Allowed: CREATED, CONFIRMED, PROCESSING, SHIPPED, DELIVERED, CANCELLED, PENDING",
                    HttpStatus.BAD_REQUEST,
                    "INVALID_STATUS"
            );
        }
        String normalized = OrderStatuses.normalize(status);
        checkoutOrderStore.updateOrderStatus(orderId, normalized);
        return toDto(requireRecord(orderId));
    }

    public AdminDashboardResponseDto dashboard() {
        List<CheckoutOrderStore.Record> orders = checkoutOrderStore.listAll();
        long pending = orders.stream()
                .filter(r -> OrderStatuses.isOpen(r.getOrderStatus()))
                .count();
        long completed = orders.stream()
                .filter(r -> OrderStatuses.isCompleted(r.getOrderStatus()))
                .count();
        BigDecimal revenue = orders.stream()
                .filter(r -> PaymentStatuses.SUCCESS.equalsIgnoreCase(r.getPaymentStatus()))
                .map(r -> r.getAmountPaid() != null ? r.getAmountPaid() : r.getExpectedAmount())
                .filter(amount -> amount != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return AdminDashboardResponseDto.builder()
                .totalProducts(productCatalog.count())
                .totalOrders(orders.size())
                .pendingOrders(pending)
                .completedOrders(completed)
                .totalRevenue(revenue)
                .build();
    }

    private CheckoutOrderStore.Record requireRecord(String orderId) {
        if (!StringUtils.hasText(orderId)) {
            throw new ResourceNotFoundException("Order not found");
        }
        CheckoutOrderStore.Record record = checkoutOrderStore.get(orderId);
        if (record == null) {
            throw new ResourceNotFoundException("Order not found: " + orderId);
        }
        return record;
    }

    private AdminOrderResponseDto toDto(CheckoutOrderStore.Record record) {
        CreateOrderRequestDto request = record.getRequest();
        return AdminOrderResponseDto.builder()
                .orderId(record.getOrderId())
                .orderStatus(record.getOrderStatus())
                .paymentStatus(record.getPaymentStatus())
                .cashfreeOrderStatus(record.getCashfreeOrderStatus())
                .expectedAmount(record.getExpectedAmount())
                .amountPaid(record.getAmountPaid())
                .cfPaymentId(record.getCfPaymentId())
                .createdAt(record.getCreatedAt())
                .lastVerifiedAt(record.getLastVerifiedAt())
                .customerDetails(request != null ? request.getCustomerDetails() : null)
                .orderSnapshot(record.snapshot())
                .cartId(request != null ? request.getCartId() : null)
                .userId(request != null ? request.getUserId() : null)
                .build();
    }
}
