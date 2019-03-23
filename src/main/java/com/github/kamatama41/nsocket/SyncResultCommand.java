package com.github.kamatama41.nsocket;

class SyncResultCommand implements Command<SyncResultData> {
    static final String COMMAND_ID = "__syncResult";
    private final CommandContext context;
    private final ObjectCodec codec;

    SyncResultCommand(Context context) {
        this.context = context.getCommandContext();
        this.codec = context.getCodec();
    }

    @Override
    public void execute(SyncResultData receivedData, Connection connection) {
        SyncResultData actualData = context.getSyncResult(receivedData.getCallId());
        Class<?> syncResultClass = context.getSyncResultClass(receivedData.getCommandId());
        actualData.setResult(codec.convert(receivedData.getResult(), syncResultClass));
        actualData.setStatus(receivedData.getStatus());
        actualData.setErrorMessage(receivedData.getErrorMessage());
        actualData.notifyCompleted();
    }

    @Override
    public String getId() {
        return COMMAND_ID;
    }
}
