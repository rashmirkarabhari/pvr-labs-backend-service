package com.pvrlabs.payment.dto.request;

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
@Schema(description = "Line item captured at checkout for order confirmation emails")
public class OrderItemSnapshotDto {

    private String productId;
    private String name;
    private String variant;
    private BigDecimal price;
    private Integer quantity;
}
