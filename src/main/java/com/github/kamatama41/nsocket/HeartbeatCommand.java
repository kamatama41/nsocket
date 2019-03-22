package com.github.kamatama41.nsocket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class HeartbeatCommand implements Command<Void> {
    static final String COMMAND_ID = "__heartbeat";
    private static final Logger log = LoggerFactory.getLogger(HeartbeatCommand.class);

    @Override
    public void execute(Void data, Connection connection) {
        log.trace("Receive heartbeat");
        connection.updateLastHeartbeatTime();
    }

    @Override
    public String getId() {
        return COMMAND_ID;
    }
}
