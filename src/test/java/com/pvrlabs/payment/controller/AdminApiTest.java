package com.pvrlabs.payment.controller;

import com.pvrlabs.payment.domain.PaymentStatuses;
import com.pvrlabs.payment.dto.request.CreateOrderRequestDto;
import com.pvrlabs.payment.dto.request.CustomerDetailsDto;
import com.pvrlabs.payment.exception.GlobalExceptionHandler;
import com.pvrlabs.payment.service.AdminOrderService;
import com.pvrlabs.payment.service.CheckoutOrderStore;
import com.pvrlabs.payment.service.ProductCatalog;
import com.pvrlabs.payment.service.ProductService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.math.BigDecimal;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminApiTest {

    private MockMvc mockMvc;
    private CheckoutOrderStore orderStore;
    private ProductCatalog catalog;

    @BeforeEach
    void setUp() {
        catalog = new ProductCatalog();
        orderStore = new CheckoutOrderStore();
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders
                .standaloneSetup(
                        new AdminProductController(new ProductService(catalog)),
                        new AdminOrderController(new AdminOrderService(orderStore, catalog)))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Test
    void getAdminProducts_returnsEmptyList() throws Exception {
        mockMvc.perform(get("/api/admin/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data", hasSize(0)));
    }

    @Test
    void createProduct_returnsCreated() throws Exception {
        mockMvc.perform(post("/api/admin/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productJson("Mini", "10.50", 3)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value("Mini"))
                .andExpect(jsonPath("$.data.price").value(10.50))
                .andExpect(jsonPath("$.data.stock").value(3))
                .andExpect(jsonPath("$.data.active").value(true))
                .andExpect(jsonPath("$.data.id").isNotEmpty());
    }

    @Test
    void updateProduct_replacesFields() throws Exception {
        String id = createProductAndReturnId();
        mockMvc.perform(put("/api/admin/products/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productJson("Updated", "20.00", 1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Updated"))
                .andExpect(jsonPath("$.data.price").value(20.00))
                .andExpect(jsonPath("$.data.stock").value(1));
    }

    @Test
    void deleteProduct_removesFromCatalog() throws Exception {
        String id = createProductAndReturnId();
        mockMvc.perform(delete("/api/admin/products/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
        mockMvc.perform(get("/api/admin/products"))
                .andExpect(jsonPath("$.data", hasSize(0)));
    }

    @Test
    void productNotFound_returns404() throws Exception {
        mockMvc.perform(put("/api/admin/products/missing-id")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productJson("Ghost", "1.00", 0)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void createProduct_invalidPrice_returns400() throws Exception {
        mockMvc.perform(post("/api/admin/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productJson("Bad", "-1.00", 1)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }

    @Test
    void getAdminOrders_returnsCheckoutRecords() throws Exception {
        seedOrder("PVR-ORD-1");
        mockMvc.perform(get("/api/admin/orders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].orderId").value("PVR-ORD-1"))
                .andExpect(jsonPath("$.data[0].orderStatus").value("PENDING"));
    }

    @Test
    void updateOrderStatus_setsFulfillmentStatus() throws Exception {
        seedOrder("PVR-ORD-2");
        mockMvc.perform(put("/api/admin/orders/PVR-ORD-2/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"SHIPPED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderStatus").value("SHIPPED"))
                .andExpect(jsonPath("$.data.paymentStatus").value("PENDING"));
    }

    @Test
    void updateOrderStatus_invalidStatus_returns400() throws Exception {
        seedOrder("PVR-ORD-3");
        mockMvc.perform(put("/api/admin/orders/PVR-ORD-3/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"NOT_A_STATUS\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_STATUS"));
    }

    @Test
    void orderNotFound_returns404() throws Exception {
        mockMvc.perform(get("/api/admin/orders/does-not-exist"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void dashboardSummary_returnsCountsAndRevenue() throws Exception {
        createProductAndReturnId();
        seedOrder("PVR-OPEN");
        seedOrder("PVR-PAID");
        orderStore.markSuccessIfFirst("PVR-PAID", new BigDecimal("99.00"), "pay_1", PaymentStatuses.CASHFREE_PAID);
        mockMvc.perform(put("/api/admin/orders/PVR-PAID/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"DELIVERED\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/admin/dashboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalProducts").value(1))
                .andExpect(jsonPath("$.data.totalOrders").value(2))
                .andExpect(jsonPath("$.data.pendingOrders").value(1))
                .andExpect(jsonPath("$.data.completedOrders").value(1))
                .andExpect(jsonPath("$.data.totalRevenue", is(99.0)));
    }

    private String createProductAndReturnId() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/admin/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productJson("Mini", "10.50", 3)))
                .andExpect(status().isCreated())
                .andReturn();
        String body = result.getResponse().getContentAsString();
        int start = body.indexOf("\"id\":\"") + 6;
        int end = body.indexOf('"', start);
        return body.substring(start, end);
    }

    private void seedOrder(String orderId) {
        CreateOrderRequestDto request = CreateOrderRequestDto.builder()
                .orderAmount(new BigDecimal("99.00"))
                .orderCurrency("INR")
                .customerDetails(CustomerDetailsDto.builder()
                        .customerId("USR-1")
                        .customerPhone("9380930486")
                        .customerEmail("buyer@example.com")
                        .customerName("Asha")
                        .build())
                .build();
        orderStore.savePending(orderId, request, new BigDecimal("99.00"));
    }

    private static String productJson(String name, String price, int stock) {
        return """
                {
                  "name": "%s",
                  "description": "PLA print",
                  "price": %s,
                  "imageUrl": "https://example.com/a.png",
                  "category": "minis",
                  "stock": %d,
                  "active": true
                }
                """.formatted(name, price, stock);
    }
}
