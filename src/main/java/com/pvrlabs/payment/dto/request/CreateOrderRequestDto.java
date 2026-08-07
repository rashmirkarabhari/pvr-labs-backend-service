package com.pvrlabs.payment.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request payload to create a Cashfree payment order")
public class CreateOrderRequestDto {

    @NotNull(message = "Order amount is required")
    @DecimalMin(value = "1.00", message = "Order amount must be at least 1.00")
    @Schema(description = "Order amount in major currency units", example = "1499.00")
    private BigDecimal orderAmount;

    @NotBlank(message = "Currency is required")
    @Pattern(regexp = "INR|USD|EUR|GBP", message = "Unsupported currency")
    @Schema(description = "ISO currency code", example = "INR")
    private String orderCurrency;

    @Size(max = 64, message = "Order ID must be at most 64 characters")
    @Schema(description = "Optional merchant order ID. Generated server-side when omitted.", example = "PVR-ORD-20260806-ABC123")
    private String orderId;

    @Size(max = 256, message = "Order note must be at most 256 characters")
    @Schema(description = "Optional note shown in Cashfree dashboard", example = "3D print – Custom figurine")
    private String orderNote;

    @NotNull(message = "Customer details are required")
    @Valid
    private CustomerDetailsDto customerDetails;

    @Schema(description = "Optional key-value tags for reconciliation with Order/Product services")
    private Map<String, String> orderTags;

    @Schema(description = "Optional internal cart / product reference for future Order service linkage")
    private String cartId;

    @Schema(description = "Optional authenticated user ID from User service")
    private String userId;
}
