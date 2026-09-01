package com.pvrlabs.payment.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Parses Cashfree PG error JSON into a user-facing message + HTTP status for Angular.
 */
@Component
@RequiredArgsConstructor
public class CashfreeErrorParser {

    private final ObjectMapper objectMapper;

    public ParsedError parse(int httpStatus, String responseBody) {
        String message = "Cashfree payment request failed";
        String cashfreeCode = null;
        String cashfreeType = null;

        if (StringUtils.hasText(responseBody)) {
            try {
                JsonNode root = objectMapper.readTree(responseBody);
                if (root.hasNonNull("message")) {
                    message = root.get("message").asText();
                }
                if (root.hasNonNull("code")) {
                    cashfreeCode = root.get("code").asText();
                }
                if (root.hasNonNull("type")) {
                    cashfreeType = root.get("type").asText();
                }
            } catch (Exception ignored) {
                message = responseBody;
            }
        }

        HttpStatus status = mapStatus(httpStatus, message);
        String code = StringUtils.hasText(cashfreeCode) ? cashfreeCode.toUpperCase() : "CASHFREE_ERROR";

        return new ParsedError(status, code, message, cashfreeType, responseBody);
    }

    private HttpStatus mapStatus(int httpStatus, String message) {
        if (httpStatus >= 400 && httpStatus < 500) {
            // Surface merchant/account misconfiguration clearly as 400 so Angular can show it.
            return HttpStatus.BAD_REQUEST;
        }
        if (httpStatus >= 500) {
            return HttpStatus.BAD_GATEWAY;
        }
        if (StringUtils.hasText(message) && message.toLowerCase().contains("not enabled")) {
            return HttpStatus.BAD_REQUEST;
        }
        return HttpStatus.BAD_GATEWAY;
    }

    public record ParsedError(
            HttpStatus status,
            String code,
            String message,
            String cashfreeType,
            String rawBody
    ) {
    }
}
