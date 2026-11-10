package com.agri_system.application.agronomy.dto;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import java.io.IOException;

/** 仅本模块的目标ID使用；不改变其他现有接口的Jackson规则。 */
public final class CaseObjectIdDeserializer extends StdDeserializer<Long> {
    public CaseObjectIdDeserializer() { super(Long.class); }

    @Override public Long deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        if (parser.currentToken() == JsonToken.VALUE_STRING || parser.currentToken() == JsonToken.VALUE_NUMBER_INT) {
            String raw = parser.getText();
            if (raw.matches("[1-9][0-9]{0,18}")) {
                try { return Long.valueOf(raw); }
                catch (NumberFormatException invalid) { /* 超出Long也不能静默转换。 */ }
            }
        }
        throw JsonMappingException.from(parser, "对象编号必须是规范正整数，不能使用小数或科学计数法");
    }
}
