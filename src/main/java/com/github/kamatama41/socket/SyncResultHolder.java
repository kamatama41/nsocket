package com.github.kamatama41.socket;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

class SyncResultHolder {
    private final ConcurrentMap<Integer, SyncResultData> map;
    private final AtomicInteger callIdCounter;

    SyncResultHolder() {
        this.map = new ConcurrentHashMap<>();
        this.callIdCounter = new AtomicInteger(0);
    }

    SyncResultData registerNewSyncResult(String commandId) {
        int callId = callIdCounter.getAndAdd(1);
        SyncResultData result = new SyncResultData(commandId, callId);
        if (map.putIfAbsent(callId, result) != null) {
            throw new IllegalStateException();
        }
        return result;
    }

    SyncResultData getSyncResult(int callId) {
        return map.get(callId);
    }
}
