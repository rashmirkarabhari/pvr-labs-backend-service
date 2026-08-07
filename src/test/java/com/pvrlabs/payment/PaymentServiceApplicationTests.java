package com.pvrlabs.payment;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = {
        "cashfree.client-id=test-client-id",
        "cashfree.client-secret=test-client-secret",
        "cashfree.environment=SANDBOX"
})
class PaymentServiceApplicationTests {

    @Test
    void contextLoads() {
        // Verifies Spring context wires with placeholder Cashfree credentials.
    }
}
