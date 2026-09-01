package com.pvrlabs.payment.dto.response;

import com.pvrlabs.payment.dto.request.CustomerDetailsDto;
import com.pvrlabs.payment.dto.request.OrderSnapshotDto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminOrderResponseDto {

    private String orderId;
    private String orderStatus;
    private String paymentStatus;
    private String cashfreeOrderStatus;
    private BigDecimal expectedAmount;
    private BigDecimal amountPaid;
    private String cfPaymentId;
    private Instant createdAt;
    private Instant lastVerifiedAt;
    private CustomerDetailsDto customerDetails;
    private OrderSnapshotDto orderSnapshot;
    private String cartId;
    private String userId;
}
