package com.pvrlabs.payment.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Safe create-order response for the Angular checkout (no secrets)")
public class CreateOrderResponseDto {

    @Schema(example = "PVR-ORD-20260806-ABC123")
    private String orderId;

    @Schema(description = "Cashfree payment_session_id used by Cashfree JS SDK on the frontend")
    private String paymentSessionId;

    private BigDecimal orderAmount;

    private String orderCurrency;

    @Schema(description = "Cashfree order status", example = "ACTIVE")
    private String orderStatus;

    @Schema(description = "SANDBOX or PRODUCTION – used by Angular to init Cashfree JS SDK mode")
    private String environment;
}
