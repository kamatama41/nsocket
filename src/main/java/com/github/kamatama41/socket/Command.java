package com.github.kamatama41.socket;

@FunctionalInterface
public interface Command<T> extends CommandId {
    void execute(T data, Connection connection) throws Exception;
}
