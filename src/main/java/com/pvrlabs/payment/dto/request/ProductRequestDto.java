package com.pvrlabs.payment.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Create or replace a catalog product")
public class ProductRequestDto {

    @NotBlank(message = "Product name is required")
    @Size(max = 200, message = "Product name must be at most 200 characters")
    private String name;

    @Size(max = 4000, message = "Description must be at most 4000 characters")
    private String description;

    @NotNull(message = "Price is required")
    @DecimalMin(value = "0.00", inclusive = true, message = "Price must be zero or greater")
    private BigDecimal price;

    @Size(max = 2048, message = "Image URL must be at most 2048 characters")
    private String imageUrl;

    @Size(max = 120, message = "Category must be at most 120 characters")
    private String category;

    @NotNull(message = "Stock is required")
    @Min(value = 0, message = "Stock must be zero or greater")
    private Integer stock;

    @Schema(description = "Whether the product is listed. Defaults to true when omitted.")
    private Boolean active;
}
