package com.harshboss.ai.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Strips PII (emails, phone numbers, SSNs, credit cards) from prompts before
 * they reach the LLM, and restores the original values in the response.
 *
 * <p>This protects user data from being sent to third-party LLM providers
 * (OWASP LLM Top 10 - LLM02: Sensitive Information Disclosure).</p>
 */
@Slf4j
@Component
public class PiiRedactor {

    private static final Pattern EMAIL = Pattern.compile("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}");
    private static final Pattern PHONE = Pattern.compile("(\\+?\\d{1,3}[-.\\s]?)?\\(?\\d{3}\\)?[-.\\s]?\\d{3}[-.\\s]?\\d{4}");
    private static final Pattern SSN = Pattern.compile("\\b\\d{3}-\\d{2}-\\d{4}\\b");
    private static final Pattern CREDIT_CARD = Pattern.compile("\\b(?:\\d[ -]*?){13,16}\\b");

    /** Thread-local mapping for the current redaction (so restore works across the same thread). */
    private final ThreadLocal<Map<String, String>> tokenMap = ThreadLocal.withInitial(LinkedHashMap::new);

    public String redact(String text) {
        if (text == null || text.isBlank()) return text;
        Map<String, String> map = tokenMap.get();
        map.clear();
        String result = text;
        result = replace(result, EMAIL, "EMAIL");
        result = replace(result, PHONE, "PHONE");
        result = replace(result, SSN, "SSN");
        result = replace(result, CREDIT_CARD, "CREDIT_CARD");
        if (!map.isEmpty()) log.debug("Redacted {} PII entities", map.size());
        return result;
    }

    public String restore(String text) {
        if (text == null || text.isBlank()) return text;
        Map<String, String> map = tokenMap.get();
        if (map.isEmpty()) return text;
        String result = text;
        for (Map.Entry<String, String> e : map.entrySet()) {
            result = result.replace(e.getKey(), e.getValue());
        }
        map.clear();
        return result;
    }

    private String replace(String text, Pattern pattern, String label) {
        Map<String, String> map = tokenMap.get();
        Matcher m = pattern.matcher(text);
        StringBuffer sb = new StringBuffer();
        int i = 1;
        while (m.find()) {
            String original = m.group();
            if (label.equals("PHONE") && original.replaceAll("\\D", "").length() < 10) continue;
            String token = "[" + label + "_" + i + "]";
            map.put(token, original);
            m.appendReplacement(sb, token);
            i++;
        }
        m.appendTail(sb);
        return sb.toString();
    }
}
