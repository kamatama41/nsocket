package com.github.kamatama41.nsocket;

import com.github.kamatama41.nsocket.codec.ObjectCodec;

import java.io.IOException;

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
        try {
            resultData.setResult(codec.decodeFromJson(resultData.getResultJson(), syncResultClass));
        } catch (IOException e) {
            resultData.setStatus(SyncResultData.Status.FAILED);
            resultData.setErrorMessage("Failed to decode from result JSON: " + resultData.getResultJson());
        }
        request.setResult(resultData);
    }

    @Override
    public String getId() {
        return ID;
    }
}
