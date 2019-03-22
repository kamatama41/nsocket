package com.github.kamatama41.socket;

class SyncResultCommand implements Command<SyncResultData> {
    static final String COMMAND_ID = "__syncResult";
    private final CommandContext context;

    SyncResultCommand(CommandContext context) {
        this.context = context;
    }

    @Override
    public void execute(SyncResultData receivedData, Connection connection) {
        SyncResultData actualData = context.getSyncResult(receivedData.getCallId());
        Class<?> syncResultClass = context.getSyncResultClass(receivedData.getCommandId());
        actualData.setResult(context.convert(receivedData.getResult(), syncResultClass));
        actualData.notifyCompleted();
    }

    @Override
    public String getId() {
        return COMMAND_ID;
    }
}
