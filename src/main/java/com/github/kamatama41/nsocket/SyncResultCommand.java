package com.github.kamatama41.nsocket;

class SyncResultCommand implements Command<SyncResultData> {
    static final String ID = "__sync_result";
    private final CommandRegistry commandRegistry;
    private final ObjectCodec codec;
    private final SyncManager syncManager;

    SyncResultCommand(Context context) {
        this.commandRegistry = context.getCommandRegistry();
        this.codec = context.getCodec();
        this.syncManager = context.getSyncManager();
    }

    @Override
    public void execute(SyncResultData resultData, Connection connection) {
        SyncManager.Request request = syncManager.getRequest(resultData.getCallId());
        Class<?> syncResultClass = commandRegistry.getSyncResultClass(resultData.getCommandId());
        resultData.setResult(codec.convert(resultData.getResult(), syncResultClass));
        request.setResult(resultData);
    }

    @Override
    public String getId() {
        return ID;
    }
}
