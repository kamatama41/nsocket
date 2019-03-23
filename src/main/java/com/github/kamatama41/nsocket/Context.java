package com.github.kamatama41.nsocket;

class Context {
    private CommandRegistry commandRegistry;
    private ObjectCodec codec;
    private SyncManager syncManager;
    private long heartBeatInterval;

    Context() {
        this.commandRegistry = new CommandRegistry();
        this.codec = new ObjectCodec();
        this.syncManager = new SyncManager();
        this.heartBeatInterval = 10000L;
    }

    CommandRegistry getCommandRegistry() {
        return commandRegistry;
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
