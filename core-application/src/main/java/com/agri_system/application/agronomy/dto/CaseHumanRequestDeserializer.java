package com.agri_system.application.agronomy.dto;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import java.io.IOException;

/** 明确求门店协助只能是真正的布尔意图，不把字符串或整数推断为同意。 */
public final class CaseHumanRequestDeserializer extends StdDeserializer<Boolean> {
    public CaseHumanRequestDeserializer() { super(Boolean.class); }
    @Override public Boolean deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        if (parser.currentToken() == JsonToken.VALUE_TRUE) return true;
        if (parser.currentToken() == JsonToken.VALUE_FALSE) return false;
        throw JsonMappingException.from(parser, "是否请求门店协助必须为布尔值");
    }
}
