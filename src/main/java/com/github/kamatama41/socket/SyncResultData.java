package com.github.kamatama41.socket;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

class SyncResultData {
    private final String commandId;
    private final int callId;
    private final CountDownLatch timer;
    private Object result;
    private Status status;

    @JsonCreator
    SyncResultData(
            @JsonProperty("commandId") String commandId,
            @JsonProperty("callId") int callId) {
        this.commandId = commandId;
        this.callId = callId;
        this.timer = new CountDownLatch(1);
    }

    @JsonProperty
    String getCommandId() {
        return commandId;
    }

    @JsonProperty
    int getCallId() {
        return callId;
    }

    @JsonProperty
    Object getResult() {
        return result;
    }

    void setResult(Object result) {
        this.result = result;
    }

    @JsonProperty
    Status getStatus() {
        return status;
    }

    void setStatus(Status status) {
        this.status = status;
    }

    void waitUntilCompleted(long timeoutMillis) throws InterruptedException {
        boolean completed = timer.await(timeoutMillis, TimeUnit.MILLISECONDS);
        if (!completed) {
            throw new CommandException("Timed out");
        }
    }

    void notifyCompleted() {
        timer.countDown();
    }

    enum Status {
        SUCCEEDED, FAILED, TIMEOUT
    }
}
