package com.github.kamatama41.nsocket;

class Context {
    private CommandRegistry commandRegistry;
    private CommandListenerRegistry listenerRegistry;
    private ObjectCodec codec;
    private SyncManager syncManager;
    private long heartBeatInterval;

    Context() {
        this.commandRegistry = new CommandRegistry();
        this.listenerRegistry = new CommandListenerRegistry();
        this.codec = new ObjectCodec();
        this.syncManager = new SyncManager();
        this.heartBeatInterval = 10000L;
    }

    CommandRegistry getCommandRegistry() {
        return commandRegistry;
    }

    CommandListenerRegistry getListenerRegistry() {
        return listenerRegistry;
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
