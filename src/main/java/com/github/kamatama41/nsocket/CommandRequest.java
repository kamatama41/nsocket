package com.github.kamatama41.nsocket;

class CommandRequest {
    private final String dataJson;
    private final Connection connection;

    CommandRequest(String dataJson, Connection connection) {
        this.dataJson = dataJson;
        this.connection = connection;
    }

    String getDataJson() {
        return dataJson;
    }

    Connection getConnection() {
        return connection;
    }
}
