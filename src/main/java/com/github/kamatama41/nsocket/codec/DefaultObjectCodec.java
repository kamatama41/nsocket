package com.github.kamatama41.nsocket.codec;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;

class DefaultObjectCodec implements ObjectCodec {
    private final ObjectMapper mapper;

    DefaultObjectCodec() {
        this.mapper = new ObjectMapper();
    }

    @Override
    public String encodeToJson(Object data) throws IOException {
        return mapper.writeValueAsString(data);
    }

    @Override
    public <T> T decodeFromJson(String json, Class<T> valueType) throws IOException {
        return mapper.readValue(json, valueType);
    }

    @Override
    public <T> T convert(Object dataObj, Class<T> dataClass) {
        return mapper.convertValue(dataObj, dataClass);
    }
}
