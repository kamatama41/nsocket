package com.github.kamatama41.socket;

class Context {
    CommandContext commandContext;
    private long heartBeatInterval;

    Context() {
        this.commandContext = new CommandContext();
        this.heartBeatInterval = 10000L;
    }

    CommandContext getCommandContext() {
        return commandContext;
    }

    long getHeartbeatInterval() {
        return heartBeatInterval;
    }
}
