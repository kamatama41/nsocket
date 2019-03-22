package com.github.kamatama41.nsocket;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;

class CommandDataCodec {
    private final ObjectMapper mapper;

    CommandDataCodec() {
        this.mapper = new ObjectMapper();
    }

    String encode(CommandData data) throws IOException {
        return mapper.writeValueAsString(data);
    }

    CommandData decode(String dataJson) throws IOException {
        return mapper.readValue(dataJson, CommandData.class);
    }

    <T> T convert(Object dataObj, Class<T> dataClass) {
        return mapper.convertValue(dataObj, dataClass);
    }
}
