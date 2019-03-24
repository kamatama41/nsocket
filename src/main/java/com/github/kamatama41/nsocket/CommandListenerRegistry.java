package com.github.kamatama41.nsocket;

import java.util.ArrayList;
import java.util.List;

class CommandListenerRegistry {
    private List<CommandListener> listeners;

    CommandListenerRegistry() {
        this.listeners = new ArrayList<>();
    }

    void registerListener(CommandListener listener) {
        this.listeners.add(listener);
    }

    void fireConnectedEvent(Connection connection) {
        for (CommandListener listener : listeners) {
            try {
                listener.onConnected(connection);
            } catch (Exception e) {
                fireExceptionEvent(connection, e);
            }
        }
    }

    void fireDisconnectedEvent(Connection connection) {
        for (CommandListener listener : listeners) {
            try {
                listener.onDisconnected(connection);
            } catch (Exception e) {
                fireExceptionEvent(connection, e);
            }
        }
    }

    void fireExceptionEvent(Connection connection, Exception ex) {
        for (CommandListener listener : listeners) {
            listener.onException(connection, ex);
        }
    }
}
