package com.github.kamatama41.nsocket;

public interface CommandListener {
    default void onConnected(Connection connection) {
    }

    default void onDisconnected(Connection connection) {
    }

    default void onException(Connection connection, Exception ex) {
    }
}
