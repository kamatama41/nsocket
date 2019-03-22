package com.github.kamatama41.nsocket;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

class CommandData {
    private final String commandId;
    private final Integer callId;
    private final Object body;

    @JsonCreator
    CommandData(
            @JsonProperty("commandId") String commandId,
            @JsonProperty("callId") Integer callId,
            @JsonProperty("body") Object body) {
        this.commandId = commandId;
        this.callId = callId;
        this.body = body;
    }

    @JsonProperty
    String getCommandId() {
        return commandId;
    }

    @JsonProperty
    Integer getCallId() {
        return callId;
    }

    @JsonProperty
    Object getBody() {
        return body;
    }
}
