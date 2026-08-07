package com.pvrlabs.payment.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Acknowledgement returned to Cashfree after webhook processing")
public class WebhookAckResponseDto {

    private boolean verified;
    private String eventType;
    private String orderId;
    private String message;
}
