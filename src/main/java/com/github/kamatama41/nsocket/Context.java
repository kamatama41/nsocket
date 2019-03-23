package com.github.kamatama41.nsocket;

class Context {
    private CommandContext commandContext;
    private ObjectCodec codec;
    private long heartBeatInterval;

    Context() {
        this.commandContext = new CommandContext();
        this.codec = new ObjectCodec();
        this.heartBeatInterval = 10000L;
    }

    CommandContext getCommandContext() {
        return commandContext;
    }

    ObjectCodec getCodec() {
        return codec;
    }

    long getHeartbeatInterval() {
        return heartBeatInterval;
    }
}
