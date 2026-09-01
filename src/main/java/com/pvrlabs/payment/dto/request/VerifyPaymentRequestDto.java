package com.pvrlabs.payment.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Optional body for explicit verification. Client-reported success flags are intentionally omitted —
 * Cashfree is the only source of payment truth.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Optional identifiers for payment verification. Status from the client is ignored.")
public class VerifyPaymentRequestDto {

    @Schema(description = "Cashfree payment id (cf_payment_id) to pin verification to a specific attempt")
    private String paymentId;
}
