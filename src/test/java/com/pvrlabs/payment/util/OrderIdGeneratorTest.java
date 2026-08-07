package com.pvrlabs.payment.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class OrderIdGeneratorTest {

    @Test
    void generatesExpectedFormat() {
        String orderId = OrderIdGenerator.generate();
        assertTrue(orderId.matches("PVR-ORD-\\d{8}-[A-Z0-9]{6}"),
                "Unexpected order id format: " + orderId);
    }
}
