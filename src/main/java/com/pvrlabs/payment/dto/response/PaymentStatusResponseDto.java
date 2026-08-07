package com.pvrlabs.payment.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Payment status response for order polling from Angular")
public class PaymentStatusResponseDto {

    private String orderId;

    @Schema(description = "Cashfree order status e.g. ACTIVE, PAID, EXPIRED", example = "PAID")
    private String orderStatus;

    private BigDecimal orderAmount;

    private String orderCurrency;

    @Schema(description = "Aggregated payment status derived from payment attempts")
    private String paymentStatus;

    private List<PaymentAttemptDto> payments;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PaymentAttemptDto {
        private String cfPaymentId;
        private String paymentStatus;
        private BigDecimal paymentAmount;
        private String paymentCurrency;
        private String paymentMethod;
        private String paymentTime;
    }
}
