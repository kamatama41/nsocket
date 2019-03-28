package com.github.kamatama41.nsocket;

public class SetConnectionIdCommand implements Command<Integer> {
    static String ID = "__set_connection_id";
    private CommandListenerRegistry listenerRegistry;

    SetConnectionIdCommand(CommandListenerRegistry listenerRegistry) {
        this.listenerRegistry = listenerRegistry;
    }

    @Override
    public void execute(Integer connectionId, Connection connection) {
        connection.setConnectionId(connectionId);
        listenerRegistry.fireConnectedEvent(connection);
    }

    @Override
    public String getId() {
        return ID;
    }
}
