package com.github.kamatama41.nsocket;

public interface Command<T> extends CommandId {
    void execute(T data, Connection connection) throws Exception;
}
