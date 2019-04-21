package com.github.kamatama41.nsocket;

import com.github.kamatama41.nsocket.codec.ObjectCodec;

class Context {
    private CommandRegistry commandRegistry;
    private CommandListenerRegistry listenerRegistry;
    private ObjectCodec codec;
    private SyncManager syncManager;
    private String name;
    private int defaultContentBufferSize;
    private int heartbeatIntervalSeconds;
    private SslContext sslContext;

    private Context(String defaultName, boolean isServer) {
        this.commandRegistry = new CommandRegistry();
        this.listenerRegistry = new CommandListenerRegistry();
        this.codec = ObjectCodec.DEFAULT;
        this.syncManager = new SyncManager();
        this.name = defaultName;
        this.defaultContentBufferSize = 8 * 1024;
        this.heartbeatIntervalSeconds = 10;
        this.sslContext = new SslContext(isServer);
    }

    static Context server() {
        return new Context("server", true);
    }

    static Context client() {
        return new Context("client", false);
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

    int getDefaultContentBufferSize() {
        return defaultContentBufferSize;
    }

    void setDefaultContentBufferSize(int defaultContentBufferSize) {
        this.defaultContentBufferSize = defaultContentBufferSize;
    }

    int getHeartbeatIntervalSeconds() {
        return heartbeatIntervalSeconds;
    }

    void setHeartbeatIntervalSeconds(int heartbeatIntervalSeconds) {
        this.heartbeatIntervalSeconds = heartbeatIntervalSeconds;
    }

    SslContext getSslContext() {
        return sslContext;
    }
}
