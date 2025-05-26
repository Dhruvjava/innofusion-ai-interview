package com.innfusion.utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class Utils {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public static String extractMessage(String raw) {
        try {
            // Find the part that looks like JSON (in case it's prefixed like "HTTP 429 - {...}")
            int jsonStart = raw.indexOf("{");
            if (jsonStart >= 0) {
                String json = raw.substring(jsonStart);
                JsonNode root = OBJECT_MAPPER.readTree(json);
                return root.path("error").path("message").asText(raw); // fallback to raw if not found
            }
        } catch (Exception e) {
            // Fallback to raw message
        }
        return raw;
    }

}
