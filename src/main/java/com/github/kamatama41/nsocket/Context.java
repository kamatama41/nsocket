package com.github.kamatama41.nsocket;

class Context {
    private CommandContext commandContext;
    private ObjectCodec codec;
    private SyncManager syncManager;
    private long heartBeatInterval;

    Context() {
        this.commandContext = new CommandContext();
        this.codec = new ObjectCodec();
        this.syncManager = new SyncManager();
        this.heartBeatInterval = 10000L;
    }

    CommandContext getCommandContext() {
        return commandContext;
    }

    ObjectCodec getCodec() {
        return codec;
    }

    SyncManager getSyncManager() {
        return syncManager;
    }

    long getHeartbeatInterval() {
        return heartBeatInterval;
    }
}
