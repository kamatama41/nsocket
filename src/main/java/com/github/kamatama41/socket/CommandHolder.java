package com.github.kamatama41.socket;

import java.lang.reflect.ParameterizedType;
import java.util.HashMap;
import java.util.Map;

class CommandHolder {
    private final Map<String, Command> commandMap;
    private final Map<String, Class<?>> dataClassMap;
    private final Map<String, SyncCommand> syncCommandMap;
    private final Map<String, Class<?>> syncResultClassMap;

    CommandHolder() {
        this.commandMap = new HashMap<>();
        this.dataClassMap = new HashMap<>();
        this.syncCommandMap = new HashMap<>();
        this.syncResultClassMap = new HashMap<>();
    }

    void registerCommand(Command command) {
        commandMap.put(command.getId(), command);
        dataClassMap.put(command.getId(), resolveDataClass(command));
    }

    void registerSyncCommand(SyncCommand command) {
        syncCommandMap.put(command.getId(), command);
        dataClassMap.put(command.getId(), resolveDataClass(command));
        syncResultClassMap.put(command.getId(), resolveSyncResultClass(command));
    }

    Command getCommand(String commandId) {
        return commandMap.get(commandId);
    }

    SyncCommand getSyncCommand(String commandId) {
        return syncCommandMap.get(commandId);
    }

    Class<?> getDataClass(String commandId) {
        return dataClassMap.get(commandId);
    }

    Class<?> getSyncResultClass(String commandId) {
        return syncResultClassMap.get(commandId);
    }

    private static Class resolveDataClass(Object commandObj) {
        Class<?> commandClass = commandObj.getClass();
        if (commandClass.getName().contains("$$Lambda$")) {
            throw new IllegalArgumentException("Lambda is not supported");
        }
        ParameterizedType type = (ParameterizedType) commandClass.getGenericInterfaces()[0];
        return (Class) type.getActualTypeArguments()[0];
    }

    private static Class resolveSyncResultClass(Object syncCommandObj) {
        Class<?> syncCommandClass = syncCommandObj.getClass();
        if (syncCommandClass.getName().contains("$$Lambda$")) {
            throw new IllegalArgumentException("Lambda is not supported");
        }
        ParameterizedType type = (ParameterizedType) syncCommandClass.getGenericInterfaces()[0];
        return (Class) type.getActualTypeArguments()[1];
    }
}
