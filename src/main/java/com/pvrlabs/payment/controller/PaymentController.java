package com.pvrlabs.payment.controller;

import com.pvrlabs.payment.dto.request.CreateOrderRequestDto;
import com.pvrlabs.payment.dto.response.ApiResponse;
import com.pvrlabs.payment.dto.response.CreateOrderResponseDto;
import com.pvrlabs.payment.dto.response.PaymentStatusResponseDto;
import com.pvrlabs.payment.dto.response.WebhookAckResponseDto;
import com.pvrlabs.payment.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@Validated
@RestController
@RequestMapping("/api/payment")
@RequiredArgsConstructor
@Tag(name = "Payment", description = "Cashfree payment APIs for PVR 3D Labs checkout")
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping("/create-order")
    @Operation(
            summary = "Create payment order",
            description = "Creates a Cashfree order and returns paymentSessionId for the Angular Cashfree JS SDK. "
                    + "Never returns Cashfree client secrets."
    )
    public ResponseEntity<ApiResponse<CreateOrderResponseDto>> createOrder(
            @Valid @RequestBody CreateOrderRequestDto request) {
        log.info("POST /api/payment/create-order | customerId={}",
                request.getCustomerDetails() != null ? request.getCustomerDetails().getCustomerId() : null);
        CreateOrderResponseDto data = paymentService.createOrder(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Order created successfully", data));
    }

    @GetMapping("/status/{orderId}")
    @Operation(
            summary = "Get payment status",
            description = "Fetches Cashfree order and payment attempt status for Angular post-checkout polling."
    )
    public ResponseEntity<ApiResponse<PaymentStatusResponseDto>> getStatus(
            @Parameter(description = "Merchant / Cashfree order ID", example = "PVR-ORD-20260806-ABC123")
            @PathVariable @NotBlank String orderId) {
        log.info("GET /api/payment/status/{}", orderId);
        PaymentStatusResponseDto data = paymentService.getPaymentStatus(orderId);
        return ResponseEntity.ok(ApiResponse.ok(data));
    }

    @PostMapping(value = "/webhook", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Cashfree webhook",
            description = "Receives Cashfree payment webhooks. Verifies x-webhook-signature using the server-side secret."
    )
    public ResponseEntity<ApiResponse<WebhookAckResponseDto>> webhook(
            @RequestBody String rawBody,
            @RequestHeader(value = "x-webhook-signature", required = false) String signature,
            @RequestHeader(value = "x-webhook-timestamp", required = false) String timestamp) {
        log.info("POST /api/payment/webhook");
        WebhookAckResponseDto data = paymentService.handleWebhook(rawBody, signature, timestamp);
        return ResponseEntity.ok(ApiResponse.ok("Webhook accepted", data));
    }
}
