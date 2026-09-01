package com.pvrlabs.payment.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Catalog product. This service had no Product model; this is the first one
 * (in-memory, matching {@code CheckoutOrderStore}), not a duplicate.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Product {

    private String id;
    private String name;
    private String description;
    private BigDecimal price;
    private String imageUrl;
    private String category;
    private Integer stock;
    private Boolean active;
    private Instant createdAt;
    private Instant updatedAt;
}
