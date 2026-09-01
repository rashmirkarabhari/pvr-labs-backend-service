package com.pvrlabs.payment.service;

import com.pvrlabs.payment.domain.Product;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory product catalog. This backend has no JPA/Product microservice yet,
 * so the catalog follows the same process-local store pattern as {@link CheckoutOrderStore}.
 */
@Component
public class ProductCatalog {

    private final ConcurrentHashMap<String, Product> products = new ConcurrentHashMap<>();

    public List<Product> findAll() {
        List<Product> snapshot = new ArrayList<>(products.values());
        snapshot.sort(Comparator.comparing(Product::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())));
        return snapshot.stream().map(this::copy).toList();
    }

    public Product findById(String id) {
        Product product = products.get(id);
        return product == null ? null : copy(product);
    }

    public Product create(Product incoming) {
        Instant now = Instant.now();
        Product stored = copy(incoming);
        stored.setId(UUID.randomUUID().toString());
        stored.setCreatedAt(now);
        stored.setUpdatedAt(now);
        if (stored.getActive() == null) {
            stored.setActive(true);
        }
        products.put(stored.getId(), stored);
        return copy(stored);
    }

    public Product replace(String id, Product incoming) {
        Product existing = products.get(id);
        if (existing == null) {
            return null;
        }
        synchronized (existing) {
            existing.setName(incoming.getName());
            existing.setDescription(incoming.getDescription());
            existing.setPrice(incoming.getPrice());
            existing.setImageUrl(incoming.getImageUrl());
            existing.setCategory(incoming.getCategory());
            existing.setStock(incoming.getStock());
            existing.setActive(incoming.getActive() != null ? incoming.getActive() : existing.getActive());
            existing.setUpdatedAt(Instant.now());
            return copy(existing);
        }
    }

    public boolean delete(String id) {
        return products.remove(id) != null;
    }

    public long count() {
        return products.size();
    }

    private Product copy(Product source) {
        return Product.builder()
                .id(source.getId())
                .name(source.getName())
                .description(source.getDescription())
                .price(source.getPrice())
                .imageUrl(source.getImageUrl())
                .category(source.getCategory())
                .stock(source.getStock())
                .active(source.getActive())
                .createdAt(source.getCreatedAt())
                .updatedAt(source.getUpdatedAt())
                .build();
    }
}
