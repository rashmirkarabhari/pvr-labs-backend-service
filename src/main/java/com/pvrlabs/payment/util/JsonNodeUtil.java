package com.pvrlabs.payment.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@RequiredArgsConstructor
public class JsonNodeUtil {

    private final ObjectMapper objectMapper;

    public JsonNode parse(String rawJson) {
        try {
            return objectMapper.readTree(rawJson);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Invalid JSON payload", ex);
        }
    }

    public String textAt(JsonNode root, String... path) {
        JsonNode current = root;
        for (String segment : path) {
            if (current == null || current.isMissingNode()) {
                return null;
            }
            current = current.get(segment);
        }
        if (current == null || current.isNull() || current.isMissingNode()) {
            return null;
        }
        String value = current.asText();
        return StringUtils.hasText(value) ? value : null;
    }
}
