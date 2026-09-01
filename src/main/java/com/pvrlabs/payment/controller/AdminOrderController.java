package com.pvrlabs.payment.controller;

import com.pvrlabs.payment.dto.request.UpdateOrderStatusRequestDto;
import com.pvrlabs.payment.dto.response.AdminDashboardResponseDto;
import com.pvrlabs.payment.dto.response.AdminOrderResponseDto;
import com.pvrlabs.payment.dto.response.ApiResponse;
import com.pvrlabs.payment.service.AdminOrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Slf4j
@Validated
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@Tag(name = "Admin Orders", description = "Order and dashboard APIs. Unprotected: this service has no auth.")
public class AdminOrderController {

    private final AdminOrderService adminOrderService;

    @GetMapping("/dashboard")
    @Operation(summary = "Lightweight admin summary counts")
    public ResponseEntity<ApiResponse<AdminDashboardResponseDto>> dashboard() {
        return ResponseEntity.ok(ApiResponse.ok(adminOrderService.dashboard()));
    }

    @GetMapping("/orders")
    @Operation(summary = "List checkout orders in this process")
    public ResponseEntity<ApiResponse<List<AdminOrderResponseDto>>> listOrders() {
        return ResponseEntity.ok(ApiResponse.ok(adminOrderService.listOrders()));
    }

    @GetMapping("/orders/{id}")
    @Operation(summary = "Get a checkout order by merchant order ID")
    public ResponseEntity<ApiResponse<AdminOrderResponseDto>> getOrder(@PathVariable @NotBlank String id) {
        return ResponseEntity.ok(ApiResponse.ok(adminOrderService.getOrder(id)));
    }

    @PutMapping("/orders/{id}/status")
    @Operation(summary = "Update fulfillment status (does not change Cashfree payment state)")
    public ResponseEntity<ApiResponse<AdminOrderResponseDto>> updateStatus(
            @PathVariable @NotBlank String id,
            @Valid @RequestBody UpdateOrderStatusRequestDto request) {
        log.info("Admin order status update orderId={} status={}", id, request.getStatus());
        AdminOrderResponseDto updated = adminOrderService.updateStatus(id, request);
        return ResponseEntity.ok(ApiResponse.ok("Order status updated", updated));
    }
}
