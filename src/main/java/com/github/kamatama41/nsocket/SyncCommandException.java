package com.github.kamatama41.nsocket;

class SyncCommandException extends RuntimeException {
    SyncCommandException(String message) {
        super(message);
    }

    SyncCommandException(Throwable cause) {
        super(cause);
    }
}
