package com.github.kamatama41.nsocket.codec;

import java.io.IOException;

public interface ObjectCodec {
    String encodeToJson(Object data) throws IOException;

    <T> T decodeFromJson(String json, Class<T> valueType) throws IOException;

    ObjectCodec DEFAULT = new DefaultObjectCodec();
}
