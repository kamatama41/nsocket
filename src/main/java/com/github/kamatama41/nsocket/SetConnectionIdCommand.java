package com.github.kamatama41.nsocket;

public class SetConnectionIdCommand implements Command<Integer> {
    static String ID = "__set_connection_id";

    @Override
    public void execute(Integer connectionId, Connection connection) {
        connection.setConnectionId(connectionId);
    }

    @Override
    public String getId() {
        return ID;
    }
}
