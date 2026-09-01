package com.pvrlabs.payment.controller;

import com.pvrlabs.payment.domain.Product;
import com.pvrlabs.payment.dto.request.ProductRequestDto;
import com.pvrlabs.payment.dto.response.ApiResponse;
import com.pvrlabs.payment.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Slf4j
@Validated
@RestController
@RequestMapping("/api/admin/products")
@RequiredArgsConstructor
@Tag(name = "Admin Products", description = "Catalog management for the admin dashboard. Unprotected: this service has no auth.")
public class AdminProductController {

    private final ProductService productService;

    @GetMapping
    @Operation(summary = "List catalog products")
    public ResponseEntity<ApiResponse<List<Product>>> list() {
        return ResponseEntity.ok(ApiResponse.ok(productService.listProducts()));
    }

    @PostMapping
    @Operation(summary = "Create a catalog product")
    public ResponseEntity<ApiResponse<Product>> create(@Valid @RequestBody ProductRequestDto request) {
        Product created = productService.createProduct(request);
        log.info("Admin created product id={}", created.getId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Product created", created));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Replace a catalog product")
    public ResponseEntity<ApiResponse<Product>> update(
            @PathVariable @NotBlank String id,
            @Valid @RequestBody ProductRequestDto request) {
        Product updated = productService.updateProduct(id, request);
        return ResponseEntity.ok(ApiResponse.ok("Product updated", updated));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a catalog product")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable @NotBlank String id) {
        productService.deleteProduct(id);
        return ResponseEntity.ok(ApiResponse.ok("Product deleted", null));
    }
}
