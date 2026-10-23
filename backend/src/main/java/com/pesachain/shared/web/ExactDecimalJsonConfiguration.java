package com.pesachain.shared.web;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import java.io.IOException;
import java.math.BigDecimal;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ExactDecimalJsonConfiguration {
    @Bean
    Jackson2ObjectMapperBuilderCustomizer exactDecimalStrings() {
        SimpleModule decimals = new SimpleModule("pesachain-exact-decimal-wire-format");
        decimals.addSerializer(BigDecimal.class, new JsonSerializer<>() {
            @Override public void serialize(BigDecimal value, JsonGenerator generator, SerializerProvider provider)
                    throws IOException {
                generator.writeString(value.toPlainString());
            }
        });
        decimals.addDeserializer(BigDecimal.class, new JsonDeserializer<>() {
            @Override public BigDecimal deserialize(JsonParser parser, DeserializationContext context) throws IOException {
                JsonToken token = parser.currentToken();
                if (token == JsonToken.VALUE_STRING) {
                    String value = parser.getText();
                    try {
                        return new BigDecimal(value);
                    } catch (NumberFormatException invalid) {
                        return (BigDecimal) context.handleWeirdStringValue(BigDecimal.class, value,
                                "Expected a decimal amount string.");
                    }
                }
                if (token == JsonToken.VALUE_NUMBER_INT || token == JsonToken.VALUE_NUMBER_FLOAT) {
                    return parser.getDecimalValue();
                }
                return (BigDecimal) context.handleUnexpectedToken(BigDecimal.class, parser);
            }
        });
        return builder -> builder.modulesToInstall(decimals);
    }
}
