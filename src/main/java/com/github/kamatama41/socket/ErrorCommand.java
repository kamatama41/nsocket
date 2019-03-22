package com.github.kamatama41.socket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ErrorCommand implements Command<ErrorData> {
    static final String COMMAND_ID = "__error";
    private static final Logger log = LoggerFactory.getLogger(ErrorCommand.class);

    @Override
    public void execute(ErrorData data, Connection connection) {
        log.warn("Received an error. CommandID: {} Message: {}", data.getCommandId(), data.getMessage());
    }

    @Override
    public String getId() {
        return COMMAND_ID;
    }
}
