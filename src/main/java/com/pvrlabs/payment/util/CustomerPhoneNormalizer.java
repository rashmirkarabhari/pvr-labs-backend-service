package com.pvrlabs.payment.util;

import com.pvrlabs.payment.exception.PaymentException;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;

/**
 * Cashfree PG customer_phone for INR checkout is a 10-digit Indian mobile number.
 * The Create Order schema uses minLength/maxLength 10. A +91 prefix can still
 * produce a payment_session_id while Hosted Checkout fails with "Something went wrong".
 */
public final class CustomerPhoneNormalizer {

    private CustomerPhoneNormalizer() {
    }

    public static String toIndianMobile(String raw) {
        if (!StringUtils.hasText(raw)) {
            throw invalid();
        }

        String trimmed = raw.trim();
        boolean plusPrefix = trimmed.startsWith("+");
        String digits = trimmed.replaceAll("\\D", "");

        if (digits.startsWith("91") && digits.length() == 12) {
            digits = digits.substring(2);
        } else if (plusPrefix && digits.startsWith("91") && digits.length() > 10) {
            digits = digits.substring(2);
        }

        if (digits.length() == 10 && digits.charAt(0) >= '6' && digits.charAt(0) <= '9') {
            return digits;
        }

        throw invalid();
    }

    private static PaymentException invalid() {
        return new PaymentException(
                "Enter a valid 10-digit Indian mobile number for payment",
                HttpStatus.BAD_REQUEST,
                "INVALID_CUSTOMER_PHONE"
        );
    }
}
