package com.pvrlabs.payment.util;

import com.pvrlabs.payment.exception.PaymentException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CustomerPhoneNormalizerTest {

    @Test
    void stripsCountryCodeAndSeparators() {
        assertEquals("9380930486", CustomerPhoneNormalizer.toIndianMobile("+91 9380930486"));
        assertEquals("9380930486", CustomerPhoneNormalizer.toIndianMobile("919380930486"));
        assertEquals("9380930486", CustomerPhoneNormalizer.toIndianMobile("9380930486"));
    }

    @Test
    void rejectsUsAndShortNumbers() {
        assertThrows(PaymentException.class, () -> CustomerPhoneNormalizer.toIndianMobile("+1 (555) 010-2000"));
        assertThrows(PaymentException.class, () -> CustomerPhoneNormalizer.toIndianMobile("12345"));
    }
}
