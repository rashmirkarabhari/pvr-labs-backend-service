package com.pvrlabs.payment.service;

import com.pvrlabs.payment.domain.Product;
import com.pvrlabs.payment.dto.request.ProductRequestDto;
import com.pvrlabs.payment.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductCatalog productCatalog;

    public List<Product> listProducts() {
        return productCatalog.findAll();
    }

    public Product getProduct(String id) {
        requireProductId(id);
        Product product = productCatalog.findById(id);
        if (product == null) {
            throw new ResourceNotFoundException("Product not found: " + id);
        }
        return product;
    }

    public Product createProduct(ProductRequestDto request) {
        return productCatalog.create(toProduct(request));
    }

    public Product updateProduct(String id, ProductRequestDto request) {
        requireProductId(id);
        Product updated = productCatalog.replace(id, toProduct(request));
        if (updated == null) {
            throw new ResourceNotFoundException("Product not found: " + id);
        }
        return updated;
    }

    public void deleteProduct(String id) {
        requireProductId(id);
        if (!productCatalog.delete(id)) {
            throw new ResourceNotFoundException("Product not found: " + id);
        }
    }

    public long countProducts() {
        return productCatalog.count();
    }

    private static void requireProductId(String id) {
        if (!StringUtils.hasText(id)) {
            throw new ResourceNotFoundException("Product not found");
        }
    }

    private static Product toProduct(ProductRequestDto request) {
        return Product.builder()
                .name(request.getName())
                .description(request.getDescription())
                .price(request.getPrice())
                .imageUrl(request.getImageUrl())
                .category(request.getCategory())
                .stock(request.getStock())
                .active(request.getActive() != null ? request.getActive() : Boolean.TRUE)
                .build();
    }
}
