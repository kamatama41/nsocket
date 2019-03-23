package com.github.kamatama41.nsocket;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;

class ObjectCodec {
    private final ObjectMapper mapper;

    ObjectCodec() {
        this.mapper = new ObjectMapper();
    }

    String encodeToJson(Object data) throws IOException {
        return mapper.writeValueAsString(data);
    }

    <T> T decodeFromJson(String json, Class<T> valueType) throws IOException {
        return mapper.readValue(json, valueType);
    }

    <T> T convert(Object dataObj, Class<T> dataClass) {
        return mapper.convertValue(dataObj, dataClass);
    }
}
