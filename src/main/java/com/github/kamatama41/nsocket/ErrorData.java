package com.github.kamatama41.nsocket;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

class ErrorData {
    private String commandId;
    private String message;

    @JsonCreator
    ErrorData(@JsonProperty("message") String message) {
        this.message = message;
    }

    @JsonProperty
    String getCommandId() {
        return commandId;
    }

    void setCommandId(String commandId) {
        this.commandId = commandId;
    }

    @JsonProperty
    String getMessage() {
        return message;
    }
}
