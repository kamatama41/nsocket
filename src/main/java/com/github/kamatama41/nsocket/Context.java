package com.github.kamatama41.nsocket;

import com.github.kamatama41.nsocket.codec.ObjectCodec;

class Context {
    private CommandRegistry commandRegistry;
    private CommandListenerRegistry listenerRegistry;
    private ObjectCodec codec;
    private SyncManager syncManager;
    private String name;
    private long heartBeatInterval;

    Context(String defaultName) {
        this.commandRegistry = new CommandRegistry();
        this.listenerRegistry = new CommandListenerRegistry();
        this.codec = ObjectCodec.DEFAULT;
        this.syncManager = new SyncManager();
        this.name = defaultName;
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

    void setCodec(ObjectCodec codec) {
        this.codec = codec;
    }

    SyncManager getSyncManager() {
        return syncManager;
    }

    String getName() {
        return name;
    }

    void setName(String name) {
        this.name = name;
    }

    long getHeartbeatInterval() {
        return heartBeatInterval;
    }
}
