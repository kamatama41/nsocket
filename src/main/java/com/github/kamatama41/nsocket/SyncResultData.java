package com.github.kamatama41.nsocket;

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
    private String errorMessage;

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

    @JsonProperty
    String getErrorMessage() {
        return errorMessage;
    }

    void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    boolean waitUntilCompleted(long timeoutMillis) throws InterruptedException {
        return timer.await(timeoutMillis, TimeUnit.MILLISECONDS);
    }

    void notifyCompleted() {
        timer.countDown();
    }

    enum Status {
        SUCCEEDED, FAILED, TIMEOUT
    }
}
