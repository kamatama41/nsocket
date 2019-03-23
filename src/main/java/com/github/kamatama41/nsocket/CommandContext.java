package com.github.kamatama41.nsocket;

class CommandContext {
    private CommandHolder commandHolder;

    CommandContext() {
        this.commandHolder = new CommandHolder();
    }

    void registerCommand(Command command) {
        commandHolder.registerCommand(command);
    }

    void registerSyncCommand(SyncCommand syncCommand) {
        commandHolder.registerSyncCommand(syncCommand);
    }

    Command getCommand(String commandId) {
        return commandHolder.getCommand(commandId);
    }

    SyncCommand getSyncCommand(String commandId) {
        return commandHolder.getSyncCommand(commandId);
    }

    Class<?> getDataClass(String commandId) {
        return commandHolder.getDataClass(commandId);
    }

    Class<?> getSyncResultClass(String commandId) {
        return commandHolder.getSyncResultClass(commandId);
    }
}
