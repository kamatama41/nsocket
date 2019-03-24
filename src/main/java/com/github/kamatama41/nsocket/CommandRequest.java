package com.github.kamatama41.nsocket;

class CommandRequest {
    private final String message;
    private final Connection connection;

    CommandRequest(String message, Connection connection) {
        this.message = message;
        this.connection = connection;
    }

    String getMessage() {
        return message;
    }

    Connection getConnection() {
        return connection;
    }
}
