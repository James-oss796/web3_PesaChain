package com.pesachain.shared.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;
import org.springframework.context.annotation.Import;

@JsonTest
@Import(ExactDecimalJsonConfiguration.class)
class ExactDecimalJsonConfigurationTest {
    @Autowired private ObjectMapper objectMapper;

    @Test
    void emitsDecimalStringsAndPreservesExactScaleAndValue() throws Exception {
        String json=objectMapper.writeValueAsString(new BigDecimal("9007199254740993.01"));
        assertEquals("\"9007199254740993.01\"",json);
        assertEquals(new BigDecimal("9007199254740993.01"),objectMapper.readValue(json,BigDecimal.class));
    }

    @Test
    void parsesCanonicalStringsAndLegacyJsonNumberTokensExactly() throws Exception {
        assertEquals(new BigDecimal("0.10"),objectMapper.readValue("\"0.10\"",BigDecimal.class));
        assertEquals(new BigDecimal("0.10"),objectMapper.readValue("0.10",BigDecimal.class));
        assertEquals(new BigDecimal("9007199254740993.01"),
                objectMapper.readValue("9007199254740993.01",BigDecimal.class));
    }

    @Test
    void serializesNegativeScaleDecimalWithoutScientificNotation() throws Exception {
        String json=objectMapper.writeValueAsString(new BigDecimal("1E+3"));
        assertTrue(json.startsWith("\""));
        assertEquals("\"1000\"",json);
    }
}
