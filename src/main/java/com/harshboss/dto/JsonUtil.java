package com.harshboss.dto;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Collections;
import java.util.List;

/**
 * Tiny helper for converting between Java Lists and the JSON-string columns
 * we use in Postgres (action_items, key_dates, attendees, alternatives).
 *
 * <p>Storing these as TEXT (rather than native JSONB) keeps the schema portable
 * and means the entities stay simple {@code String} fields. All the
 * (de)serialization happens here so the rest of the codebase deals in
 * {@code List<String>}.</p>
 */
public final class JsonUtil {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<List<String>> LIST_OF_STRING = new TypeReference<>() {};

    private JsonUtil() {}

    /** Serialize a list to a JSON array string. Returns "[]" for null/empty. */
    public static String toJson(List<String> list) {
        if (list == null || list.isEmpty()) {
            return "[]";
        }
        try {
            return MAPPER.writeValueAsString(list);
        } catch (JsonProcessingException e) {
            return "[]";
        }
    }

    /** Deserialize a JSON array string to a List. Returns empty list on any error. */
    public static List<String> fromJson(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }
        try {
            List<String> result = MAPPER.readValue(json, LIST_OF_STRING);
            return result == null ? Collections.emptyList() : result;
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    /** Expose the ObjectMapper for parsing complex JSON (e.g. tool call results). */
    public static ObjectMapper getObjectMapper() {
        return MAPPER;
    }
}
