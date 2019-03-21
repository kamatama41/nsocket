package com.github.kamatama41.socket;

@FunctionalInterface
public interface SyncCommand<T, R> extends CommandId {
    R apply(T data, Connection connection) throws Exception;

    default long getTimeoutMillis() {
        return 30000L;
    }
}
