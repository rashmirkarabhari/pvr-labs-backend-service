package com.pvrlabs.payment.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Fulfillment status update for an existing checkout order")
public class UpdateOrderStatusRequestDto {

    @NotBlank(message = "Status is required")
    @Schema(example = "SHIPPED", allowableValues = {
            "CREATED", "CONFIRMED", "PROCESSING", "SHIPPED", "DELIVERED", "CANCELLED", "PENDING"
    })
    private String status;
}
