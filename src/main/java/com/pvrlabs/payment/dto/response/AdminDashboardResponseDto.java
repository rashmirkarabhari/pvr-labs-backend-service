package com.pvrlabs.payment.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminDashboardResponseDto {

    private long totalProducts;
    private long totalOrders;
    private long pendingOrders;
    private long completedOrders;
    private BigDecimal totalRevenue;
}
