package com.github.kamatama41.nsocket;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

class SyncManager {
    private final ConcurrentMap<Integer, Request> map;
    private final AtomicInteger callIdCounter;

    SyncManager() {
        this.map = new ConcurrentHashMap<>();
        this.callIdCounter = new AtomicInteger(0);
    }

    Request registerNewRequest() {
        int callId = callIdCounter.getAndAdd(1);
        Request request = new Request(callId);
        if (map.putIfAbsent(callId, request) != null) {
            throw new IllegalStateException();
        }
        return request;
    }

    Request getRequest(int callId) {
        return map.get(callId);
    }

    private void removeRequest(Request request) {
        map.remove(request.getCallId(), request);
    }

    class Request {
        private final int callId;
        private final CountDownLatch timer;
        private SyncResultData result;

        Request(int callId) {
            this.callId = callId;
            this.timer = new CountDownLatch(1);
        }

        int getCallId() {
            return callId;
        }

        SyncResultData getResult() {
            return result;
        }

        void setResult(SyncResultData result) {
            this.result = result;
            // Notify completed
            timer.countDown();
        }

        boolean waitUntilCompleted(long timeoutMillis) throws InterruptedException {
            try {
                return timer.await(timeoutMillis, TimeUnit.MILLISECONDS);
            } finally {
                removeRequest(this);
            }
        }
    }
}
