package com.pvrlabs.payment.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Customer details required by Cashfree")
public class CustomerDetailsDto {

    @NotBlank(message = "Customer ID is required")
    @Size(max = 50, message = "Customer ID must be at most 50 characters")
    @Schema(description = "Unique customer identifier (map from User service)", example = "USR-10042")
    private String customerId;

    @NotBlank(message = "Customer phone is required")
    @Pattern(regexp = "^\\+?[0-9]{8,15}$", message = "Phone must be 8–15 digits, optional leading +")
    @Schema(description = "Indian mobile number. +91 is stripped before Cashfree; API expects 10 digits.", example = "9380930486")
    private String customerPhone;

    @Email(message = "Invalid email address")
    @Schema(description = "Customer email", example = "buyer@example.com")
    private String customerEmail;

    @Size(max = 100)
    @Schema(description = "Customer full name", example = "Asha Sharma")
    private String customerName;
}
