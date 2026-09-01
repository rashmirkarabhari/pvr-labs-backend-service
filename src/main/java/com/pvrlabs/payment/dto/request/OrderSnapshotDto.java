package com.pvrlabs.payment.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Checkout cart snapshot used only after Cashfree confirms payment")
public class OrderSnapshotDto {

    @Valid
    @Builder.Default
    private List<OrderItemSnapshotDto> items = new ArrayList<>();

    private BigDecimal subtotal;
    private BigDecimal discount;
    private BigDecimal delivery;
    private BigDecimal tax;
    private BigDecimal giftWrap;
    private BigDecimal total;
    private String couponCode;
    private String paymentMethod;

    @Valid
    private ShippingAddressDto shipping;
}
